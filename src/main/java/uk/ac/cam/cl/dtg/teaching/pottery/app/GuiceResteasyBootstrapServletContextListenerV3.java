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
package uk.ac.cam.cl.dtg.teaching.pottery.app;

import com.google.common.collect.ImmutableList;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;

@WebListener
public class GuiceResteasyBootstrapServletContextListenerV3
    extends GuiceResteasyBootstrapServletContextListener {

  private static Injector injector;

  public static Injector getInjector() {
    return injector;
  }

  @Override
  public void contextInitialized(ServletContextEvent event) {
    super.contextInitialized(event);
  }

  @Override
  protected List<? extends Module> getModules(ServletContext context) {
    return ImmutableList.of(new ApplicationModule(context));
  }

  @Override
  protected void withInjector(Injector i) {
    injector = i;
  }
}
