package uk.ac.cam.cl.dtg.teaching.pottery.repo;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.UUIDGenerator;
import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;

@Singleton
public class RepoFactory {
	
	/**
	 * This object is used to generate new uuids for repos
	 */
	private UUIDGenerator uuidGenerator = new UUIDGenerator();
	
	private Database database;
	
	// We need to ensure that only onle Repo object exists for any repoId so that 
	// we guarantee mutual exclusion on the filesystem operations. So we cache created objects
	// here.
	private LoadingCache<String, Repo> cache = 
			CacheBuilder.newBuilder().
			softValues().
			build(new CacheLoader<String,Repo>() {
				@Override
				public Repo load(String key) throws Exception {
					return Repo.openRepo(key, config, database);
				}
			});


	private Config config;
	
	@Inject
	public RepoFactory(Config config, Database database) {
		this.database = database;
		this.config = config;
		for(File f : config.getRepoRoot().listFiles()) {
			if (f.getName().startsWith(".")) continue;
			String uuid = f.getName();
			uuidGenerator.reserve(uuid);
		}
	}
	
	public Repo getInstance(String repoId) throws RepoException {
		try {
			return cache.get(repoId);
		} catch (ExecutionException e) {
			// this is thrown if an exception is thrown in the load method of the cache.
			if (e.getCause() instanceof RepoException) {
				throw (RepoException)e.getCause();
			}
			else {
				throw new Error(e);
			}
		}
	}

	public Repo createInstance(TaskInfo t) throws RepoException {
		final String newRepoId = uuidGenerator.generate();
		try {
			return cache.get(newRepoId, new Callable<Repo>() {
				@Override
				public Repo call() throws Exception {
					return Repo.createRepo(newRepoId, t, config, database);			
				}
			});
		} catch (ExecutionException e) {
			if (e.getCause() instanceof RepoException) {
				throw (RepoException)e.getCause();
			}
			else {
				throw new Error(e);
			}
		}
	}
}