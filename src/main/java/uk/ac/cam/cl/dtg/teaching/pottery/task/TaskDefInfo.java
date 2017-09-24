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
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;

public class TaskDefInfo {

  public static final String REMOTE_UNSET = "";

  private String taskId;

  private String registeredTag;

  private String testingCopyId;

  private String registeredCopyId;

  private boolean retired;

  /**
   * If this is non-empty then read the task definition from here rather than using local storage.
   */
  private String remote;

  public TaskDefInfo() {}

  public TaskDefInfo(
      String taskId,
      String registeredTag,
      String testingCopyId,
      String registeredCopyId,
      boolean retired,
      String remote) {
    super();
    this.taskId = taskId;
    this.registeredTag = registeredTag;
    this.testingCopyId = testingCopyId;
    this.registeredCopyId = registeredCopyId;
    this.retired = retired;
    this.remote = remote;
  }

  public static TaskDefInfo getByTaskId(String taskId, QueryRunner q) throws SQLException {
    return q.query(
        "SELECT * from tasks where taskid=?", new BeanHandler<>(TaskDefInfo.class), taskId);
  }

  public static ImmutableList<String> getAllTaskIds(QueryRunner q) throws SQLException {
    return q.query(
        "Select taskId from tasks",
        rs -> {
          ImmutableList.Builder<String> builder = ImmutableList.builder();
          while (rs.next()) {
            builder.add(rs.getString(1));
          }
          return builder.build();
        });
  }

  public static void updateRegisteredCopy(String taskId, String tag, String copyId, QueryRunner q)
      throws SQLException {
    q.update(
        "UPDATE tasks set registeredtag=?,registeredCopyId=? where taskid = ?",
        tag,
        copyId,
        taskId);
  }

  public static void updateTestingCopy(String taskId, String copyId, QueryRunner q)
      throws SQLException {
    q.update("UPDATE tasks set testingCopyId=? where taskid = ?", copyId, taskId);
  }

  public static void updateRetired(String taskId, boolean retired, QueryRunner q)
      throws SQLException {
    q.update("UPDATE tasks set retired=? where taskid = ?", retired, taskId);
  }

  public URI getTaskDefLocation(TaskConfig taskConfig) throws URISyntaxException {
    if (getRemote().equals(REMOTE_UNSET)) {
      return new URI(
          String.format("file://%s", taskConfig.getLocalTaskDefinitionDir(getTaskId()).getPath()));
    } else {
      return new URI(getRemote());
    }
  }

  public String getTestingCopyId() {
    return testingCopyId;
  }

  public void setTestingCopyId(String testingCopyId) {
    this.testingCopyId = testingCopyId;
  }

  public String getRegisteredCopyId() {
    return registeredCopyId;
  }

  public void setRegisteredCopyId(String registeredCopyId) {
    this.registeredCopyId = registeredCopyId;
  }

  public boolean isRetired() {
    return retired;
  }

  public void setRetired(boolean retired) {
    this.retired = retired;
  }

  public String getTaskId() {
    return taskId;
  }

  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  public String getRegisteredTag() {
    return registeredTag;
  }

  public void setRegisteredTag(String registeredTag) {
    this.registeredTag = registeredTag;
  }

  public String getRemote() {
    return remote;
  }

  public void setRemote(String remote) {
    this.remote = remote;
  }

  public void insert(QueryRunner q) throws SQLException {
    q.update(
        "INSERT INTO tasks(taskid,registeredtag,retired,remote) values (?,?,?,?)",
        taskId,
        registeredTag,
        retired,
        remote);
  }

  @Override
  public String toString() {
    return "TaskDefInfo{"
        + "taskId='"
        + taskId
        + '\''
        + ", registeredTag='"
        + registeredTag
        + '\''
        + ", testingCopyId='"
        + testingCopyId
        + '\''
        + ", registeredCopyId='"
        + registeredCopyId
        + '\''
        + ", retired="
        + retired
        + ", remote='"
        + remote
        + '\''
        + '}';
  }
}
