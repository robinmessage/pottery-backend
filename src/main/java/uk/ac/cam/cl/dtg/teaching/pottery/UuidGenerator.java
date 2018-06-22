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
package uk.ac.cam.cl.dtg.teaching.pottery;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class UuidGenerator {

  private Set<String> allocated;

  public UuidGenerator() {
    allocated = new HashSet<>();
  }

  public synchronized void reserve(String uuid) {
    allocated.add(uuid);
  }

  /** Generate a new unique UUID. */
  public synchronized String generate() {
    while (true) {
      String result = UUID.randomUUID().toString();
      if (!allocated.contains(result)) {
        allocated.add(result);
        return result;
      }
    }
  }
}
