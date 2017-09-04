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

package uk.ac.cam.cl.dtg.teaching.pottery.database;

import com.mchange.v2.c3p0.DataSources;
import java.sql.SQLException;
import javax.sql.DataSource;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;

public class InMemoryDatabase implements Database {

  private DataSource dataSource;
  private SQLException connectionException;

  public InMemoryDatabase() {
    try {
      dataSource = DataSources.unpooledDataSource("jdbc:hsqldb:mem:mymemdb","SA","");
    } catch (SQLException e) {
      connectionException = e;
    }
  }

  @Override
  public TransactionQueryRunner getQueryRunner() throws SQLException {
    if (dataSource == null) {
      throw connectionException;
    }
    return new TransactionQueryRunner(dataSource);
  }

  @Override
  public void stop() {
  }
}