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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.UuidGenerator;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidTaskSpecificationException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RetiredTaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskCopyNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.BuilderInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Job;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

/**
 * The definition of a task is a git repository containing info about the task, skeleton files,
 * harness and validator. This is stored in [task_root]/def.
 *
 * <p>Tasks have a testing version which is tracks HEAD of the task definition repository.
 *
 * <p>Tasks can also have a registered version which corresponds to a chosen tag in the definition
 * repository.
 *
 * <p>The testing and registered versions are stored as copies of the relevant files from the git
 * repo in [task_root]/copy/*
 *
 * <p>A task can only be deleted if there are no repositories which are attempts at the task
 *
 * <p>A task can be retired which means that it is no longer available for new repositories.
 *
 * @author acr31
 */
public class Task {

  protected static final Logger LOG = LoggerFactory.getLogger(Task.class);

  /**
   * The UUID for the task. This is constant and doesn't change throughout the lifecycle of the
   * task.
   */
  private final String taskId;

  /** The taskdef is a bare git repo which is the upstream source for the task. */
  private final URI taskDefLocation;

  private final TaskConfig config;
  /** Used to generate unique taskIds and unique copyIds. */
  private final UuidGenerator uuidGenerator;
  /**
   * Mutex for managing access to the registration fields (registeredbuilder and registeredcopy).
   */
  private final Object registeredMutex = new Object();
  /** This mutex is used to protect access to testingBuilder and testingCopy. */
  private final Object testingMutex = new Object();

  private AtomicBoolean retired;
  /**
   * Object for building a new registered version of this task and representing the progress through
   * that job. Should never be null
   */
  private volatile TaskCopyBuilder registeredBuilder;
  /**
   * Object representing the registered version of this task. If its null there is no registered
   * version.
   */
  private volatile TaskCopy registeredCopy;

  // *** BEGIN METHODS FOR MANAGING REGISTRATION OF THE TASK AND THE REGISTERED COPY ***
  /**
   * Object for building a new testing version of the task and representing the progress through
   * that job. Should never be null. Access to this object should be protected using the
   * testingMutex object.
   */
  private volatile TaskCopyBuilder testingBuilder;
  /**
   * Object representing the testing version of the task. If its null there is no testing version
   * (or one is in progress of being built).
   */
  private volatile TaskCopy testingCopy;

  /**
   * Private constructor. TaskFactory creates instances of this class calling static constructor
   * methods.
   */
  private Task(
      String taskId,
      URI taskDefLocation,
      TaskCopyBuilder testingBuilder,
      TaskCopy testingCopy,
      TaskCopyBuilder registeredBuilder,
      TaskCopy registeredCopy,
      boolean retired,
      TaskConfig config,
      UuidGenerator uuidGenerator) {
    super();
    this.taskId = taskId;
    this.taskDefLocation = taskDefLocation;
    this.testingBuilder = testingBuilder;
    this.testingCopy = testingCopy;
    this.registeredBuilder = registeredBuilder;
    this.registeredCopy = registeredCopy;
    this.retired = new AtomicBoolean(retired);
    this.config = config;
    this.uuidGenerator = uuidGenerator;
  }

  /**
   * Open an existing task and return the Task object. Don't call this method directly. Instead use
   * TaskFactory.
   */
  static Task openTask(
      String taskId, UuidGenerator uuidGenerator, Database database, TaskConfig config)
      throws InvalidTaskSpecificationException, TaskStorageException, TaskNotFoundException,
          TaskCopyNotFoundException {

    try (TransactionQueryRunner q = database.getQueryRunner()) {
      TaskDefInfo info = TaskDefInfo.getByTaskId(taskId, q);
      if (info != null) {
        URI taskDefLocation = info.getTaskDefLocation(config);

        TaskCopyBuilder testingBuilder =
            info.getTestingCopyId() == null
                ? TaskCopyBuilder.createSuccessPlaceholder("HEAD", taskId, config)
                : TaskCopyBuilder.createForExisting(
                    "HEAD", taskId, taskDefLocation, info.getTestingCopyId(), config);
        TaskCopyBuilder registeredBuilder =
            info.getRegisteredCopyId() == null
                ? TaskCopyBuilder.createSuccessPlaceholder("HEAD", taskId, config)
                : TaskCopyBuilder.createForExisting(
                    info.getRegisteredTag(),
                    taskId,
                    taskDefLocation,
                    info.getRegisteredCopyId(),
                    config);

        return new Task(
            info.getTaskId(),
            taskDefLocation,
            testingBuilder,
            testingBuilder.getTaskCopy(),
            registeredBuilder,
            registeredBuilder.getTaskCopy(),
            info.isRetired(),
            config,
            uuidGenerator);
      } else {
        throw new TaskNotFoundException("Task " + taskId + " not found");
      }
    } catch (SQLException e) {
      throw new TaskStorageException("Failed to open task " + taskId, e);
    } catch (URISyntaxException e) {
      throw new TaskStorageException("Failed to parse task location", e);
    }
  }

  /**
   * Create a new task from the standard template. Don't call this method directly. Instead use
   * TaskFactory.
   */
  static Task createTask(
      String taskId, UuidGenerator uuidGenerator, TaskConfig config, Database database)
      throws TaskStorageException {

    // create the task directory and clone the template
    File taskDefDir = config.getLocalTaskDefinitionDir(taskId);
    try {
      if (!taskDefDir.mkdir()) {
        throw new TaskStorageException("Failed to create local definition directory");
      }
      try {
        Git.init().setBare(true).setDirectory(taskDefDir).call().close();
      } catch (GitAPIException e) {
        throw new TaskStorageException("Failed to initialize git repository", e);
      }
      return storeTask(taskId, "", uuidGenerator, config, database);
    } catch (TaskStorageException e) {
      try {
        FileUtil.deleteRecursive(taskDefDir);
      } catch (IOException e1) {
        e.addSuppressed(e1);
      }
      throw e;
    }
  }

  /**
   * Create a new remote task which is stored on another server. Don't call this method directly.
   * Instead use TaskFactory.
   */
  static Task createRemoteTask(
      String taskId,
      String remote,
      UuidGenerator uuidGenerator,
      TaskConfig config,
      Database database)
      throws TaskStorageException {

    // Preflight
    try {
      Git.lsRemoteRepository().setRemote(remote).call();
    } catch (GitAPIException e) {
      throw new TaskStorageException("Unable to connect to remote repository", e);
    }

    return storeTask(taskId, remote, uuidGenerator, config, database);
  }

  private static Task storeTask(
      String taskId,
      String remote,
      UuidGenerator uuidGenerator,
      TaskConfig config,
      Database database)
      throws TaskStorageException {
    TaskDefInfo info = new TaskDefInfo(taskId, null, null, null, false, remote);
    TaskCopyBuilder testingBuilder =
        TaskCopyBuilder.createSuccessPlaceholder("HEAD", taskId, config);
    TaskCopyBuilder registeredBuilder =
        TaskCopyBuilder.createSuccessPlaceholder("HEAD", taskId, config);
    // Mark status as success so that we don't try to compile these
    testingBuilder.getBuilderInfo().setStatus(BuilderInfo.STATUS_SUCCESS);
    registeredBuilder.getBuilderInfo().setStatus(BuilderInfo.STATUS_SUCCESS);
    try (TransactionQueryRunner q = database.getQueryRunner()) {
      info.insert(q);
      q.commit();
      return new Task(
          taskId,
          info.getTaskDefLocation(config),
          testingBuilder,
          null,
          registeredBuilder,
          null,
          false,
          config,
          uuidGenerator);
    } catch (SQLException e) {
      throw new TaskStorageException("Failed to store information about task " + taskId, e);
    } catch (URISyntaxException e) {
      throw new TaskStorageException("Failed to parse URI for task location", e);
    }
  }

  public String getTaskId() {
    return taskId;
  }

  public boolean isRetired() {
    return retired.get();
  }

  public URI getTaskDefLocation() {
    return taskDefLocation;
  }

  // *** END METHODS FOR MANAGING THE REGISTERED COPY OF THE TASK ***

  // *** BEGIN METHODS FOR MANAGING THE TESTING COPY OF THE TASK ***

  public void setRetired(Database database) throws RetiredTaskException, TaskStorageException {
    if (retired.getAndSet(true)) {
      throw new RetiredTaskException("Cannot retire task " + taskId);
    }
    try (TransactionQueryRunner q = database.getQueryRunner()) {
      TaskDefInfo.updateRetired(taskId, true, q);
      q.commit();
    } catch (SQLException e) {
      retired.set(false);
      throw new TaskStorageException(
          "Failed to update database with task retirement status for taskId " + taskId, e);
    }
  }

  /** Get a reference to the RegisteredCopy object. You must call release/close when finished. */
  public TaskCopy acquireRegisteredCopy() throws TaskNotFoundException {
    TaskCopy tc = registeredCopy;
    if (tc != null && tc.acquire()) {
      return tc;
    } else {
      throw new TaskNotFoundException("Registered version of task " + taskId + " is not available");
    }
  }

  public BuilderInfo getRegisteredCopyBuilderInfo() {
    // no synchronization needed since registeredbuilder is volatile and assignments are atomic
    return registeredBuilder.getBuilderInfo();
  }

  public BuilderInfo scheduleBuildRegisteredCopy(String sha1, Worker w)
      throws RetiredTaskException {
    if (retired.get()) {
      throw new RetiredTaskException("Cannot schedule registration for task " + taskId);
    }
    // We need to ensure there is only one thread in this region at a time
    // or else we have a race between deciding that we should start a new copy and updating
    // registeredBuilder to record it
    synchronized (registeredMutex) {
      if (!registeredBuilder.isReplacable()) {
        return registeredBuilder.getBuilderInfo();
      }

      try {
        if ("HEAD".equals(sha1)) {
          sha1 = getHeadSha();
        }
        registeredBuilder =
            TaskCopyBuilder.createNew(
                sha1, taskId, taskDefLocation, uuidGenerator.generate(), config);

        registeredBuilder.schedule(
            w,
            new Job() {
              @Override
              public int execute(
                  TaskIndex taskIndex,
                  RepoFactory repoFactory,
                  ContainerManager containerManager,
                  Database database) {
                if (storeNewRegisteredCopy(w, database)) {
                  return Job.STATUS_OK;
                } else {
                  return Job.STATUS_RETRY;
                }
              }

              @Override
              public String getDescription() {
                return "Storing new registered copy of task " + taskId;
              }
            });
      } catch (TaskStorageException e) {
        registeredBuilder = TaskCopyBuilder.createFailurePlaceholder(sha1, config, e);
      }

      return registeredBuilder.getBuilderInfo();
    }
  }

  private boolean storeNewRegisteredCopy(Worker w, Database database) {
    // We need to ensure that only one thread is in this region at a time for any particular task
    // instance
    // or else we have a race on reading the old value of registeredCopy and replacing it with the
    // new one
    synchronized (registeredMutex) {
      TaskCopy oldCopy = registeredCopy;
      try (TransactionQueryRunner q = database.getQueryRunner()) {
        TaskDefInfo.updateRegisteredCopy(
            taskId,
            registeredBuilder.getBuilderInfo().getSha1(),
            registeredBuilder.getTaskCopy().getCopyId(),
            q);
        q.commit();
        registeredCopy = registeredBuilder.getTaskCopy();
        destroyTaskCopy(oldCopy, w);
      } catch (SQLException e) {
        registeredBuilder
            .getBuilderInfo()
            .setException(new TaskStorageException("Failed to record changes in database", e));
        destroyTaskCopy(registeredBuilder.getTaskCopy(), w);
        return false;
      }
      return true;
    }
  }

  public TaskCopy acquireTestingCopy() throws TaskNotFoundException {
    TaskCopy tc = testingCopy;
    if (tc != null && tc.acquire()) {
      return tc;
    } else {
      throw new TaskNotFoundException("Testing version of task " + taskId + " is not available");
    }
  }

  public BuilderInfo getTestingCopyBuilderInfo() {
    // No mutex needed since testingBuilder is volatile and updates to it are atomic
    return testingBuilder.getBuilderInfo();
  }

  // *** END METHODS FOR MANAGING TEST COPY ***

  public BuilderInfo scheduleBuildTestingCopy(Worker w) throws RetiredTaskException {
    if (retired.get()) {
      throw new RetiredTaskException("Cannot schedule registration for task " + taskId);
    }
    // We need to ensure there is only one thread in this region at a time
    // or else we have a race between deciding that we should start a new copy and updating
    // testingBuilder to record it
    synchronized (testingMutex) {
      if (!testingBuilder.isReplacable()) {
        return testingBuilder.getBuilderInfo();
      }

      LOG.info("Scheduling testing build for " + taskDefLocation);

      try {
        String headSha = getHeadSha();
        testingBuilder =
            TaskCopyBuilder.createNew(
                headSha, taskId, taskDefLocation, uuidGenerator.generate(), config);

        testingBuilder.schedule(
            w,
            new Job() {
              @Override
              public int execute(
                  TaskIndex taskIndex,
                  RepoFactory repoFactory,
                  ContainerManager containerManager,
                  Database database) {
                if (storeNewTestCopy(w, database)) {
                  return Job.STATUS_OK;
                } else {
                  return Job.STATUS_RETRY;
                }
              }

              @Override
              public String getDescription() {
                return "Storing new testing copy of " + taskId;
              }
            });
      } catch (TaskStorageException e) {
        testingBuilder = TaskCopyBuilder.createFailurePlaceholder(taskId, config, e);
      }

      return testingBuilder.getBuilderInfo();
    }
  }

  private boolean storeNewTestCopy(Worker w, Database database) {
    // We need to ensure that only one thread is in this region at a time for any particular task
    // instance
    // or else we have a race on reading the old value of testingCopy and replacing it with the new
    // one
    synchronized (testingMutex) {
      TaskCopy oldCopy = testingCopy;
      try (TransactionQueryRunner q = database.getQueryRunner()) {
        TaskDefInfo.updateTestingCopy(taskId, testingBuilder.getTaskCopy().getCopyId(), q);
        q.commit();
        testingCopy = testingBuilder.getTaskCopy();
        destroyTaskCopy(oldCopy, w);
        return true;
      } catch (SQLException e) {
        testingBuilder
            .getBuilderInfo()
            .setException(new TaskStorageException("Failed to record changes in database", e));
        destroyTaskCopy(testingBuilder.getTaskCopy(), w);
        return false;
      }
    }
  }

  /**
   * Lookup the SHA1 for the HEAD of the repo.
   *
   * @return a string containing the SHA1
   * @throws TaskStorageException if an error occurs trying to read the repo
   */
  public String getHeadSha() throws TaskStorageException {
    try {
      for (Ref r :
          Git.lsRemoteRepository()
              .setRemote(taskDefLocation.toString())
              .setHeads(true)
              .setTags(false)
              .call()) {
        if (r.getName().equals(Constants.R_HEADS + Constants.MASTER)) {
          return r.getObjectId().getName();
        }
      }
    } catch (GitAPIException e) {
      throw new TaskStorageException("Failed to read Git repository for " + taskDefLocation, e);
    }
    throw new TaskStorageException("Failed to find head reference");
  }

  /** Schedule the deletion of this taskcopy. */
  private void destroyTaskCopy(TaskCopy c, Worker w) {
    if (c != null) {
      w.schedule(
          new Job() {
            @Override
            public int execute(
                TaskIndex taskIndex,
                RepoFactory repoFactory,
                ContainerManager containerManager,
                Database database) {
              try {
                c.destroy();
                return Job.STATUS_OK;
              } catch (IOException e) {
                LOG.error("IOException attempting to destroy task copy id {}", c.getCopyId(), e);
              } catch (InterruptedException e) {
                LOG.error(
                    "Interrupted when attempting to destroy task copy id {}", c.getCopyId(), e);
              }
              return Job.STATUS_FAILED;
            }

            @Override
            public String getDescription() {
              return "Destroy task copy of task " + taskId;
            }
          });
    }
  }
}
