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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectReader;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidTaskSpecificationException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.LanguagesInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.model.TaskInfo;


class TaskInfoWrapper {
  private final LanguagesInfo languagesInfo;
  private final Map<String, TaskInfo> taskInfoMap;
  private final TaskInfo baseTaskInfo;
  private final boolean simpleTask;

  TaskInfoWrapper(LanguagesInfo languagesInfo, Map<String, TaskInfo> taskInfoMap, TaskInfo baseTaskInfo) {
    this.languagesInfo = languagesInfo;
    this.taskInfoMap = taskInfoMap;
    this.baseTaskInfo = baseTaskInfo;
    this.simpleTask = false;
  }

  TaskInfoWrapper(TaskInfo singleTask) {
    String taskLanguage = singleTask.getLanguage();
    languagesInfo = new LanguagesInfo(Collections.singletonList(taskLanguage), taskLanguage);
    taskInfoMap = Collections.singletonMap(taskLanguage, singleTask);
    baseTaskInfo = singleTask;
    simpleTask = true;
  }

  public boolean isSimpleTask() {
    return simpleTask;
  }

  public Set<String> getLanguages() {return languagesInfo.getLanguages();}
  public TaskInfo getTaskInfo(String language) {
    assert languagesInfo.getLanguages().contains(language);
    return taskInfoMap.get(language);
  }
  public TaskInfo getBaseTaskInfo() {return baseTaskInfo;}
}

public class TaskInfos {

  /** Read the json file specifying this TaskInfo from disk and parse it into an object. */
  public static TaskInfoWrapper load(String taskId, File taskDirectory, TaskConfig taskConfig)
      throws InvalidTaskSpecificationException, TaskStorageException {
    ObjectMapper o = new ObjectMapper();
    LanguagesInfo languagesInfo;

    try {
      languagesInfo = o.readValue(new File(taskDirectory, "languages.json"), LanguagesInfo.class);
    } catch (IOException e) {
      // No languages; load baseline instead
      return new TaskInfoWrapper(loadSimpleTaskInfo(taskId, taskDirectory, taskConfig));
    }

    try {
      TaskInfo baseTask = o.readValue(new File(taskDirectory, "task.base.json"), TaskInfo.class);

      Map<String, TaskInfo> taskInfoMap = languagesInfo.getLanguages().stream().collect(Collectors.toMap(Function.identity(), (language) -> {
        try {
          TaskInfo t = baseTask.clone();
          ObjectReader taskBaseReader = o.readerForUpdating(t).withType(TaskInfo.class);

          taskBaseReader.readValue(new File(taskDirectory, "task." + language + ".json"));
          t.setTaskId(taskId);
          t.setLanguage(language);
          if (t.getStartingPointFiles() == null) {
            t.setStartingPointFiles(Collections.unmodifiableList(new ArrayList<>(listSkeleton(taskConfig.getSkeletonDir(taskDirectory), language, taskId))));
          }
          return t;
        } catch (IOException|TaskStorageException e) {
          throw new RuntimeException(e);
        }
      }));

      return new TaskInfoWrapper(languagesInfo, taskInfoMap, baseTask);
    } catch (IOException|RuntimeException e) {
      throw new InvalidTaskSpecificationException(
          "Failed to load task information for task " + taskId, e);
    }
  }

  private static TaskInfo loadSimpleTaskInfo(String taskId, File taskDirectory, TaskConfig taskConfig) throws TaskStorageException, InvalidTaskSpecificationException {
    ObjectMapper o = new ObjectMapper();
    try {

      TaskInfo t = o.readValue(new File(taskDirectory, "task.json"), TaskInfo.class);
      t.setTaskId(taskId);
      if (t.getStartingPointFiles() == null) {
        t.setStartingPointFiles(Collections.unmodifiableList(new ArrayList<>(listSkeleton(taskConfig.getSkeletonDir(taskDirectory), taskId))));
      }
      return t;
    } catch (IOException e) {
      throw new InvalidTaskSpecificationException(
          "Failed to load task information for task " + taskId, e);
    }
  }

  /** Read the json files specifying this TaskInfo from disk and parse it into an object. */
  /*public static TaskInfo load(String taskId, File taskDirectory, List<String> skeletonFiles, String language)
      throws InvalidTaskSpecificationException {
    ObjectMapper o = new ObjectMapper();

    LanguagesInfo l;
    try {
      l = o.readValue(new File(taskDirectory, "languages.json"), LanguagesInfo.class);
    } catch (IOException e) {
      // No languages; load baseline and then check language is correct
      TaskInfo t = load(taskId, taskDirectory, skeletonFiles);
      if (t.getLanguage() != language) {
        throw new InvalidTaskSpecificationException("Incorrect language specified for single language task " + taskId, e);
      }
      return t;
    }

    try {
      if (!l.getLanguages().contains(language)) {
        throw new InvalidTaskSpecificationException("Language " + language + " not available in task " + taskId);
      }

      TaskInfo baseTask = o.readValue(new File(taskDirectory, "task.base.json"), TaskInfo.class);
      TaskInfo t = o.readValue(new File(taskDirectory, "task." + language + ".json"), TaskInfo.class);

      if (t.getType() == null) {
        t.setType(baseTask.getType());
      }

      if (t.getName() == null) {
        t.setName(baseTask.getName());
      }

      t.getCriteria().addAll(baseTask.getCriteria());

      if (t.getImage() == null) {
        t.setImage(baseTask.getImage());
      }

      if (t.getDifficulty() == null) {
        t.setDifficulty(baseTask.getDifficulty());
      }

      if (t.getRecommendedTimeMinutes() == 0) {
        t.setRecommendedTimeMinutes(baseTask.getRecommendedTimeMinutes());
      }

      if (t.getLanguage() == null) {
        t.setLanguage(baseTask.getLanguage());
      }

      if (t.getProblemStatement() == null) {
        t.setProblemStatement(baseTask.getProblemStatement());
      }

      if (t.getCompilationRestrictions() == ContainerRestrictions.STANDARD_CANDIDATE_RESTRICTIONS) {
        t.setCompilationRestrictions(baseTask.getCompilationRestrictions());
      }

      if (t.getHarnessRestrictions() == ContainerRestrictions.STANDARD_CANDIDATE_RESTRICTIONS) {
        t.setHarnessRestrictions(baseTask.getHarnessRestrictions());
      }

      if (t.getValidatorRestrictions() == ContainerRestrictions.STANDARD_CANDIDATE_RESTRICTIONS) {
        t.setValidatorRestrictions(baseTask.getValidatorRestrictions());
      }

      if (t.getTaskCompilationRestrictions() == ContainerRestrictions.STANDARD_AUTHOR_RESTRICTIONS) {
        t.setTaskCompilationRestrictions(baseTask.getTaskCompilationRestrictions());
      }

      t.setTaskId(taskId);
      if (t.getStartingPointFiles() == null) {
        t.setStartingPointFiles(Collections.unmodifiableList(new ArrayList<>(skeletonFiles)));
      }
      return t;
    } catch (IOException e) {
      throw new InvalidTaskSpecificationException("Error loading language " + language + " for task " + taskId, e);
    }
  }*/

  private static List<String> listSkeleton(File skeletonDir, String language, String taskId) throws TaskStorageException {
    return listSkeleton(new File(skeletonDir.toPath() + "." + language), taskId);
  }

  private static List<String> listSkeleton(File skeletonDir, String taskId) throws TaskStorageException {
    if (!skeletonDir.exists()) {
      return new LinkedList<>();
    }

    try {
      List<String> result = new LinkedList<>();
      Files.walkFileTree(
          skeletonDir.toPath(),
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              Path localLocation = skeletonDir.toPath().relativize(file);
              result.add(localLocation.toString());
              return FileVisitResult.CONTINUE;
            }
          });
      return result;
    } catch (IOException e) {
      throw new TaskStorageException(
          "Failed to access skeleton files for task "
              + taskId
              + " stored in copy at "
              + skeletonDir.getPath(),
          e);
    }
  }
}
