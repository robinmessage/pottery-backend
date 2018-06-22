/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015-2018 BlueOptima Limited, Andrew Rice (acr31@cam.ac.uk)
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
package uk.ac.cam.cl.dtg.teaching.pottery.containers;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ContainerExecutionException;

abstract class Binding {

  private static final Pattern COMMAND_TOKENIZER =
      Pattern.compile("([^\"' ]|\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*')+");
  static final String POTTERY_BINARIES_PATH = "/pottery-binaries";
  static final String IMAGE_BINDING = "IMAGE";
  static final String SUBMISSION_BINDING = "SUBMISSION";
  static final String VARIANT_BINDING = "VARIANT";
  static final String TASK_BINDING = "TASK";
  static final String STEP_BINDING = "STEP";
  static final String SHARED_BINDING = "SHARED";
  static final String DEFAULT_EXECUTION = "default";
  private static Pattern bindingRegex = Pattern.compile("@([a-zA-Z_][-a-zA-Z_0-9]*)@");

  static ExecutionConfig.Builder applyBindings(
      String command,
      ImmutableMap<String, Binding> bindings,
      Map<String, ContainerExecResponse> stepResults,
      File containerTempDir,
      String internalMountPath)
      throws ContainerExecutionException {
    ExecutionConfig.Builder builder = ExecutionConfig.builder();
    StringBuilder finalCommand = new StringBuilder();

    Map<String, Binding> mutableBindings = new HashMap<String, Binding>(bindings);

    Matcher regexMatcher = bindingRegex.matcher(command);
    while (regexMatcher.find()) {
      String name = regexMatcher.group(1);
      Binding binding;
      if (mutableBindings.containsKey(name)) {
        binding = mutableBindings.get(name);
      } else if (stepResults.containsKey(name)) {
        File stepFile = new File(containerTempDir, name);
        try (FileWriter w = new FileWriter(stepFile)) {
          w.write(stepResults.get(name).response());
        } catch (IOException e) {
          throw new ContainerExecutionException(
              "Couldn't create temporary file for binding " + name + " for command " + command, e);
        }
        binding = new FileBinding(stepFile, false, internalMountPath);
        mutableBindings.put(name, binding);
      } else {
        throw new ContainerExecutionException(
            "Couldn't find a binding called " + name + " for command " + command);
      }
      binding.applyBinding(builder, name);
      regexMatcher.appendReplacement(finalCommand, binding.getMountPoint(name));
    }
    regexMatcher.appendTail(finalCommand);

    Matcher matcher = COMMAND_TOKENIZER.matcher(finalCommand);
    while (matcher.find()) {
      builder.addCommand(matcher.group());
    }
    return builder;
  }

  abstract String getMountPoint(String name);

  ExecutionConfig.Builder applyBinding(ExecutionConfig.Builder builder, String name) {
    return builder;
  }

  static class FileBinding extends Binding {
    private final File file;
    private final boolean readWrite;
    private final String internalMountPath;
    private boolean needsApplying;

    FileBinding(File file, boolean readWrite, String internalMountPath) {
      this.file = file;
      this.readWrite = readWrite;
      this.internalMountPath = internalMountPath;
      this.needsApplying = true;
    }

    @Override
    ExecutionConfig.Builder applyBinding(ExecutionConfig.Builder builder, String name) {
      if (needsApplying) {
        needsApplying = false;

        return builder.addPathSpecification(
            PathSpecification.create(file, getMountPoint(name), readWrite));
      } else {
        return builder;
      }
    }

    @Override
    String getMountPoint(String name) {
      return internalMountPath + "/" + name;
    }
  }

  static class ImageBinding extends Binding {
    private final String path;

    ImageBinding(String path) {
      this.path = path;
    }

    @Override
    String getMountPoint(String name) {
      return path;
    }
  }

  static class TextBinding extends Binding {
    private final String text;

    TextBinding(String text) {
      this.text = text;
    }

    /** Technically, this isn't a mount point, it's just a parameter. */
    @Override
    String getMountPoint(String name) {
      return text;
    }
  }
}
