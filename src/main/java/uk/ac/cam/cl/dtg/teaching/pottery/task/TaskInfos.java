/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015-2018 Andrew Rice (acr31@cam.ac.uk), BlueOptima Limited
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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidTaskSpecificationException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.TaskInfo;

public class TaskInfos {

  /** Read the json file specifying this TaskInfo from disk and parse it into an object. */
  public static TaskInfo load(String taskId, File taskDirectory)
      throws InvalidTaskSpecificationException {
    ObjectMapper o = new ObjectMapper();
    try {
      TaskInfo t = o.readValue(new File(taskDirectory, "task.json"), TaskInfo.class);
      t.setTaskId(taskId);
      return t;
    } catch (IOException e) {
      throw new InvalidTaskSpecificationException(
          "Failed to load task information for task " + taskId, e);
    }
  }

  /** Write this task info back to disk. */
  public static void save(TaskInfo taskInfo, File taskDirectory) throws IOException {
    ObjectMapper o = new ObjectMapper();
    o.writeValue(new File(taskDirectory, "task.json"), taskInfo);
  }
}
