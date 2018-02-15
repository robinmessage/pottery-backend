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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidTaskSpecificationException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.TaskInfo;

@Singleton
public class TaskIndex {

  protected static final Logger LOG = LoggerFactory.getLogger(TaskIndex.class);

  private Map<String, Task> definedTasks;

  /** Create a new TaskIndex. */
  @Inject
  public TaskIndex(TaskFactory taskFactory, Database database) throws TaskStorageException {
    this.definedTasks = new ConcurrentHashMap<>();
    for (String taskId : getAllTaskIds(database)) {
      try {
        Task t = taskFactory.getInstance(taskId);
        definedTasks.put(taskId, t);
      } catch (TaskNotFoundException | InvalidTaskSpecificationException | TaskStorageException e) {
        LOG.warn("Ignoring task " + taskId, e);
      }
    }
  }

  /** Get the testing version of all non-retired tasks. */
  public Collection<TaskInfo> getTestingTasks() {
    List<TaskInfo> r = new LinkedList<>();
    for (Task t : definedTasks.values()) {
      if (!t.isRetired()) {
        try (TaskCopy c = t.acquireTestingCopy()) {
          r.add(c.getBaseInfo());
        } catch (TaskNotFoundException e) {
          // Ignore missing tasks
        }
      }
    }
    return r;
  }

  /** Get the registered version (if registered) of all non-retired tasks. */
  public Collection<TaskInfo> getRegisteredTasks() {
    List<TaskInfo> r = new LinkedList<>();
    for (Task t : definedTasks.values()) {
      if (!t.isRetired()) {
        try (TaskCopy c = t.acquireRegisteredCopy()) {
          r.add(c.getBaseInfo());
        } catch (TaskNotFoundException e) {
          // Ignore unregistered tasks
        }
      }
    }
    return r;
  }

  /** Get the TaskInfo for the testing version of this task. */
  public TaskInfo getTestingTaskInfo(String taskId) throws TaskNotFoundException {
    try (TaskCopy t = getTask(taskId).acquireTestingCopy()) {
      return t.getBaseInfo();
    }
  }

  /** Get the TaskInfo for the registered version of this task. */
  public TaskInfo getRegisteredTaskInfo(String taskId) throws TaskNotFoundException {
    try (TaskCopy t = getTask(taskId).acquireRegisteredCopy()) {
      return t.getBaseInfo();
    }
  }

  /** Get the Task definition object for this task. */
  public Task getTask(String taskId) throws TaskNotFoundException {
    Task t = definedTasks.get(taskId);
    if (t == null) {
      throw new TaskNotFoundException("Failed to find task " + taskId);
    }
    return t;
  }

  /** Return a list of non-retired tasks. */
  public Collection<String> getAllTasks() {
    return definedTasks
        .values()
        .stream()
        .filter(t -> !t.isRetired())
        .map(Task::getTaskId)
        .collect(toImmutableList());
  }

  /** Return a list of retired tasks. */
  public Collection<String> getRetiredTasks() {
    return definedTasks
        .values()
        .stream()
        .filter(Task::isRetired)
        .map(Task::getTaskId)
        .collect(toImmutableList());
  }

  public void add(Task newTask) {
    definedTasks.put(newTask.getTaskId(), newTask);
  }

  private static ImmutableList<String> getAllTaskIds(Database database)
      throws TaskStorageException {
    ImmutableList<String> taskIds;
    try (TransactionQueryRunner q = database.getQueryRunner()) {
      taskIds = TaskDefInfo.getAllTaskIds(q);
    } catch (SQLException e1) {
      throw new TaskStorageException("Failed to load list of tasks from database", e1);
    }
    return taskIds;
  }
}
