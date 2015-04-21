package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import java.util.Collection;
import java.util.UUID;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

import uk.ac.cam.cl.dtg.teaching.pottery.Progress;
import uk.ac.cam.cl.dtg.teaching.pottery.Store;
import uk.ac.cam.cl.dtg.teaching.pottery.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;

@Produces("application/json")
@Path("/tasks")
public class TasksController {

	private static final Logger log = LoggerFactory.getLogger(TasksController.class);
	
	@Inject
	Store store;
	
	@GET
	@Path("/")
	public Collection<Task> listAllTasks() {
		return store.tasks.values();
	}
	
	@POST
	@Path("/")
	public Progress beginTask(@FormParam("taskId") String taskId) throws TaskNotFoundException {
		Task t = store.tasks.get(taskId);
		if (t == null) throw new TaskNotFoundException();

		Progress p = new Progress();
		p.setTaskId(taskId);
		p.setProgressId(UUID.randomUUID().toString());
		store.progress.put(p.getProgressId(), p);
		return p;
	}
}
