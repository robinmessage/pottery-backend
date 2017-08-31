/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015 Andrew Rice (acr31@cam.ac.uk)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.ac.cam.cl.dtg.teaching.pottery;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class FileUtil {

	
	public static void mkdir(File dir) throws IOException {
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				throw new IOException("Failed to create directory "+dir);
			}
		}
	}
	
	public static void deleteRecursive(final File dir) throws IOException {
		if (!dir.exists()) return;
		Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file,
					BasicFileAttributes attrs) throws IOException {
				if (FileUtil.isParent(dir, file.toFile())) {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				} else {
					throw new IOException("File not within parent directory");
				}
			}
	
			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc)
					throws IOException {
				if (exc != null) {
					throw exc;
				}
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
	
		});
	}

	/**
	 * Check whether one file is a descendant of the other.  
	 * 
	 * descendant is first converted to its canonical file.  We then walk upwards.  If we find the parent file then we return true.
	 * @param parent
	 * @param descendant
	 * @return true if descendant is a descendant of parent
	 * @throws IOException
	 */
	public static boolean isParent(File parent, File descendant) throws IOException {
		descendant = descendant.getCanonicalFile();
		do {
			if (descendant.equals(parent))
				return true;
		} while ((descendant = descendant.getParentFile()) != null);
		return false;
	}
}
