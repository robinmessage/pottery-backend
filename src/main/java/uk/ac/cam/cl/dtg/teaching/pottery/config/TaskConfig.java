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

package uk.ac.cam.cl.dtg.teaching.pottery.config;

import java.io.File;
import javax.inject.Inject;
import javax.inject.Named;

public class TaskConfig {

  private File taskPrefix;

  @Inject
  public TaskConfig(@Named("localStoragePrefix") String prefix) {
    this.taskPrefix = new File(prefix, "tasks");
  }

  public File getTaskDefinitionRoot() {
    return new File(taskPrefix, "def");
  }

  public File getTaskCopyRoot() {
    return new File(taskPrefix, "copy");
  }

  public File getTaskCopyDir(String copyId) {
    return new File(getTaskCopyRoot(), copyId);
  }

  public File getSolutionDir(String copyId) {
    return new File(getTaskCopyDir(copyId), "solution");
  }

  public File getCompileDir(String copyId) {
    return new File(getTaskCopyDir(copyId), "compile");
  }

  public File getHarnessDir(String copyId) {
    return new File(getTaskCopyDir(copyId), "harness");
  }

  public File getValidatorDir(String copyId) {
    return new File(getTaskCopyDir(copyId), "validator");
  }

  public File getSkeletonDir(String copyId) {
    return getSkeletonDir(getTaskCopyDir(copyId));
  }

  public File getSkeletonDir(File copyDirectory) {
    return new File(copyDirectory, "skeleton");
  }

  public File getLocalTaskDefinitionDir(String taskId) {
    return new File(getTaskDefinitionRoot(), taskId);
  }
}
