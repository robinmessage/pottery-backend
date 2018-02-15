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

package uk.ac.cam.cl.dtg.teaching.pottery.task;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.TwoPhaseLatch;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidTaskSpecificationException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.TaskInfo;

/**
 * A task copy is a checkout of a task. Its made by copying files out of the taskdef git repo. Task
 * copies are readonly - you can't change the files in them. A task copy contains a TwoPhaseLatch,
 * when you start using a TaskCopy you acquire access through the latch and release it when you call
 * close (use try with resources). Anyone who wants to replace this TaskCopy with another one must
 * wait until all the latches are released.
 *
 * @author acr31
 */
public class TaskCopy implements AutoCloseable {

  private String copyId;
  private TaskConfig config;
  private TaskInfoWrapper info;
  private TwoPhaseLatch latch = new TwoPhaseLatch();

  /**
   * Instances of this object are created by the TaskCopyBuilder object. Its up to the
   * TaskCopyBuilder object to ensure that there is only instance of this object for each copyId
   *
   * @param taskId the ID of the task itself
   * @param copyId the unique ID for this copy
   * @param config information for tasks
   * @throws InvalidTaskSpecificationException if we can't load the task specification
   * @throws TaskStorageException if the task cannot be accessed
   */
  TaskCopy(String taskId, String copyId, TaskConfig config)
      throws InvalidTaskSpecificationException, TaskStorageException {
    super();
    this.copyId = copyId;
    this.config = config;
    this.info = TaskInfos.load(taskId, config.getTaskCopyDir(copyId), config);
  }

  public String getCopyId() {
    return copyId;
  }

  public File getLocation() {
    return config.getTaskCopyDir(copyId);
  }

  public TaskInfo getBaseInfo() {
    return this.info.getBaseTaskInfo();
  }

  public ImmutableSet<String> getLanguages() {
    return ImmutableSet.copyOf(info.getLanguages());
  }

  public Language getLanguage(String language) throws InvalidTaskSpecificationException {
    if (!info.getLanguages().contains(language)) {
      throw new InvalidTaskSpecificationException("Language " + language + " is not available for copy " + copyId);
    }

    return new Language(language, info.isSimpleTask());
  }

  public Iterable<Language> getCopyInEachLanguage() {
    return info.getLanguages().stream().map(language -> new Language(language, info.isSimpleTask())).collect(Collectors.toList());
  }

  public class Language {
    private final String language;
    private final boolean simpleTask;

    Language(String language, boolean simpleTask) {
      this.language = language;
      this.simpleTask = simpleTask;
    }

    public TaskInfo getInfo() {
      return info.getTaskInfo(language);
    }

    public File getLocation() {
      return TaskCopy.this.getLocation();
    }

    private File getLanguageDir(File directory) {
      if (simpleTask) return directory;
      return new File(directory + "." + language);
    }

    /**
     * Copy the skeleton files from this task copy to the target directory given (this will be in a
     * candidates repo).
     */
    public ImmutableList<String> copySkeleton(File destination) throws IOException {
      return FileUtil.copyFilesRecursively(getLanguageDir(config.getSkeletonDir(copyId)), destination);
    }

    public File getCompileRoot() {
      return getLanguageDir(config.getCompileDir(copyId));
    }

    public File getHarnessRoot() {
      return getLanguageDir(config.getHarnessDir(copyId));
    }

    public File getValidatorRoot() {
      return getLanguageDir(config.getValidatorDir(copyId));
    }
  }

  public boolean acquire() {
    return latch.acquire();
  }

  /** Delete this copy from the disk. */
  void destroy() throws IOException, InterruptedException {
    latch.await();
    FileUtil.deleteRecursive(config.getTaskCopyDir(copyId));
  }

  @Override
  public void close() {
    latch.release();
  }
}
