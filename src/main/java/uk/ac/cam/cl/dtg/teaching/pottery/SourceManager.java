package uk.ac.cam.cl.dtg.teaching.pottery;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.NoHeadInRepoException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class SourceManager {
	
	private File repoRoot;
	private File testingRoot;
	private String headTag;
	private String webtagPrefix;
	
	private Map<String,Repo> repos = new ConcurrentHashMap<String,Repo>();

	private static Logger log = LoggerFactory.getLogger(SourceManager.class);
	
	@Inject
	public SourceManager(Config config) {
		super();
		repoRoot = config.getRepoRoot();
		testingRoot = config.getTestingRoot();
		headTag = config.getHeadTag();
		webtagPrefix = config.getWebtagPrefix();
	}

	public String getHeadTag() { return headTag; }
	
	public Repo getRepo(String repoId) { return repos.get(repoId); }
	
	public Repo createRepo(String taskId) throws RepoException, IOException {
		
		Path repoDir = Files.createTempDirectory(repoRoot.toPath(), "");
		
		String repoId = repoDir.getFileName().toString();
		try {
			Git.init().setDirectory(repoDir.toFile()).call().close();
		}
		catch (GitAPIException e) { 
			FileUtil.deleteRecursive(repoDir.toFile());
			throw new RepoException("Failed to initialise git repository",e); 
		}
		
		Repo r = new Repo();
		r.setRepoId(repoId);
		r.setTaskId(taskId);
		
		repos.put(repoId, r);
		
		return r;
	}
	
	/**
	 * Recursively copy the files from the sourceLocation to the chosen repo. Add them and commit them to the repo.
	 * 
	 * @param repoId
	 * @param sourceLocation
	 * @throws IOException
	 * @throws RepoException 
	 */
	public void copyFiles(String repoId, File sourceLocation) throws IOException, RepoException {
		List<String> copiedFiles = new LinkedList<>();
		
		File repoDir = new File(repoRoot,repoId);
		
		Files.walkFileTree(sourceLocation.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file,
					BasicFileAttributes attrs) throws IOException {
				
				File originalFile = file.toFile();
				Path localLocation = sourceLocation.toPath().relativize(file);
				copiedFiles.add(localLocation.toString());
				File newLocation = repoDir.toPath().resolve(localLocation).toFile();
				File newDir = newLocation.getParentFile();
				log.error("Copying {} to {}",originalFile, newLocation);

				if (newDir.exists()) {
					newDir.mkdirs();
				}
				
				try(FileOutputStream fos = new FileOutputStream(newLocation)) {
					try(FileInputStream fis = new FileInputStream(originalFile)) {
						IOUtils.copy(fis, fos);
					}
				}
				return FileVisitResult.CONTINUE;
			}
		});
		
		Git git = Git.open(repoDir);
		try {
			for(String f : copiedFiles) {
				git.add().addFilepattern(f).call();
			}
			git.commit().setMessage("Copied files").call();
		} catch (GitAPIException e) {
			try {
				git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
				throw new RepoException("Failed to commit update after copying files. Rolled back",e);
			} catch (GitAPIException e1) {
				e1.addSuppressed(e);
				throw new RepoException("Failed to rollback failed update",e1);
			}
		}
		
		
	}
	
	
	public File cloneForTesting(String repoId, String tag) throws IOException, RepoException {
		File newLocation = new File(testingRoot,repoId);
		if (newLocation.exists()) {
			FileUtil.deleteRecursive(newLocation);
		}
		if (!newLocation.mkdirs()) {
			throw new IOException("Failed to create testing directory "+newLocation);
		}
		try {
			Git.cloneRepository()
			.setURI(new File(repoRoot,repoId).getPath())
			.setDirectory(newLocation)
			.setBranch(tag)
			.call()
			.close();
		} catch (GitAPIException e) {
			throw new RepoException("Failed to copy repository",e);
		}
		return newLocation;
	}
	
	
	public boolean existsTag(String repoId,String tag) throws IOException {
		Git git = Git.open(new File(repoRoot,repoId));
		try {
			return git.getRepository().resolve(Constants.R_TAGS+tag) != null;
		}
		finally {
			git.close();
		}
	}
	
	public List<String> listFiles(String repoId, String tag) throws RepoException, IOException {
		Git git = Git.open(new File(repoRoot,repoId));
		Repository repo = git.getRepository();
		RevWalk revWalk = new RevWalk(repo);
		
		List<String> result;
		try {
			RevTree tree = getRevTree(tag, repo, revWalk); 
			
			TreeWalk treeWalk = new TreeWalk(repo);
			treeWalk.addTree(tree);
			treeWalk.setRecursive(true);
			result = new LinkedList<String>();
			while(treeWalk.next()) {
				result.add(treeWalk.getNameString());
			}
			revWalk.dispose();
			return result;
		} catch (NoHeadInRepoException e) {
			return new LinkedList<String>();
		} 
		finally {
			repo.close();
		}
		
	}



	private RevTree getRevTree(String tag, Repository repo, RevWalk revWalk)
			throws AmbiguousObjectException, IncorrectObjectTypeException,
			IOException, RepoException, MissingObjectException, NoHeadInRepoException {
		RevTree tree;
		try {
			ObjectId tagId = repo.resolve(headTag.equals(tag) ? Constants.HEAD : Constants.R_TAGS+tag);
			if (tagId == null) {
				if (headTag.equals(tag)) {
					throw new NoHeadInRepoException("Failed to find HEAD in repo.");
				}
				else {
					throw new RepoException("Failed to find tag "+tag);
				}
			}
			RevCommit revCommit = revWalk.parseCommit(tagId);
			tree = revCommit.getTree();

		} catch (RevisionSyntaxException e) {
			throw new RepoException("Failed to load revision for head of repository",e);
		}
		return tree;
	}

	public String newTag(String repoId) throws IOException, RepoException {
		synchronized (getMutex(repoId)) {	
			File repoDir = new File(repoRoot,repoId);
			Git git = Git.open(repoDir);
			List<Ref> tagList;
			try {
				tagList = git.tagList().call();
			} catch (GitAPIException e) {
				throw new RepoException("Failed to list all tags in repo",e);
			}
			
			String prefix = Constants.R_TAGS+webtagPrefix;
			int max = -1;
			for(Ref tag : tagList) {
				String tagName = tag.getName();
				if (tagName.startsWith(prefix)) {
					int value;
					try {
						value = Integer.parseInt(tagName.substring(prefix.length()));
					} catch (NumberFormatException e) {
						throw new RepoException("Failed to parse tag name "+tagName);						
					}
					if (value > max) max = value;
				}				
			}
			
			String newTag = webtagPrefix + String.format("%03d", (max+1));
			
			try {
				git.tag().setName(newTag).call();
			} catch (GitAPIException e) {
				throw new RepoException("Failed to apply tag "+newTag+" to repo");
			}
						
			git.close();
			
			return newTag;
		}
	}
	
	public void updateFile(String repoId, String fileName, byte[] data) throws RepoException, IOException {
		synchronized (getMutex(repoId)) {
			File repoDir = new File(repoRoot,repoId);
			File f = new File(repoDir,fileName);
			if (!FileUtil.isParent(repoRoot, f)) {
				throw new IOException("Invalid fileName "+fileName);
			}
			if (f.isDirectory()) {
				throw new IOException("File already exists and is a directory");
			}
			
			File parentFile = f.getParentFile();
			
			if (!parentFile.exists()) {
				if (!parentFile.mkdirs()) {
					throw new IOException("Failed to create directories for "+fileName);
				}
			}
			try(FileOutputStream fos = new FileOutputStream(f)) {
				IOUtils.write(data, fos);
			}
			Git git = Git.open(repoDir);
			try {
				git.add().addFilepattern(fileName).call();
				git.commit().setMessage("Updating file "+fileName).call();
			} catch (GitAPIException e) {
				try {
					git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
					throw new RepoException("Failed to commit update to "+fileName+". Rolled back",e);
				} catch (GitAPIException e1) {
					e1.addSuppressed(e);
					throw new RepoException("Failed to rollback failed update to "+fileName,e1);
				}
			}
		}
	}
	
	public StreamingOutput readFile(String repoId, String tag, String fileName) throws IOException, RepoException {
		Git git = Git.open(new File(repoRoot,repoId));
		Repository repo = git.getRepository();
		RevWalk revWalk = new RevWalk(repo);
		RevTree tree;
		try {
			tree = getRevTree(tag, repo, revWalk);
		} catch (NoHeadInRepoException e) {
			throw new IOException("File not found");
		} 
		
		TreeWalk treeWalk = new TreeWalk(repo);
		treeWalk.addTree(tree);
		treeWalk.setRecursive(true);
		treeWalk.setFilter(PathFilter.create(fileName));
		if(!treeWalk.next()) {
			throw new IOException("File not found");
		}
		
        ObjectId objectId = treeWalk.getObjectId(0);
        ObjectLoader loader = repo.open(objectId);
        
        return new StreamingOutput() {
			@Override
			public void write(OutputStream output) throws IOException,
					WebApplicationException {
				output.write(loader.getBytes());
				revWalk.dispose();
				repo.close();				
			}
        	
        };
  	}
	
	private Map<String,Object> repoLocks = new ConcurrentHashMap<String, Object>();
	public Object getMutex(String repoId) {
		Object mutex = repoLocks.get(repoId);
		if (mutex == null) {
			synchronized (repoLocks) {
				mutex = repoLocks.get(repoId);
				if (mutex == null) {
					mutex = new Object();
					repoLocks.put(repoId, mutex);
				}
			}
		}
		return mutex;
	}
	
}
