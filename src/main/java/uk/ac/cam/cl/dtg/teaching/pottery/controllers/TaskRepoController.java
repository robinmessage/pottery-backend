package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.eclipse.jgit.api.errors.GitAPIException;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.ApiOperation;

import uk.ac.cam.cl.dtg.teaching.pottery.app.RegistrationTag;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskRepo;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskRepoInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskTestResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidTagFormatException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskRepoException;
import uk.ac.cam.cl.dtg.teaching.pottery.managers.TaskManager;
import uk.ac.cam.cl.dtg.teaching.pottery.managers.TaskRepoManager;

@Produces("application/json")
@Path("/taskrepos")
public class TaskRepoController {

	private TaskRepoManager taskRepoManager;
	private TaskManager taskManager;
	
	@Inject
	public TaskRepoController(TaskRepoManager taskRepoManager, TaskManager taskManager) {
		this.taskRepoManager = taskRepoManager;
		this.taskManager = taskManager;
	}
	
	@POST
	@Path("/")
	@ApiOperation(value="Create a new task repository",response=TaskRepo.class)
	public TaskRepo create() throws TaskRepoException {
		try {
			return taskRepoManager.create();
		} catch (IOException e) {
			throw new TaskRepoException("Failed to create task repository",e);
		}
	}
	
	@GET
	@Path("/")
	@ApiOperation(value="Return a list of task-repos",response=TaskRepoInfo.class,responseContainer="List")
	public List<TaskRepoInfo> list() throws IOException, GitAPIException {

		Map<String,TaskRepoInfo> data = new HashMap<>();
		for(String taskRepoId : taskRepoManager.scanForTaskRepositories()) {
			data.put(taskRepoId, new TaskRepoInfo(taskRepoId));
		}
		
		Map<String,RegistrationTag> registrations = taskRepoManager.scanForTasks();
		for(Map.Entry<String,RegistrationTag> e : registrations.entrySet()) {
			String taskId = e.getKey();
			RegistrationTag r = e.getValue();
			String taskRepoId = r.getRepository().getName();
			Task t = taskManager.getTask(taskId);
			data.get(taskRepoId).getRegisteredTasks().add(t);
		}
		
		List<TaskRepoInfo> result = new LinkedList<>(data.values());
		Collections.sort(result);
		return result;
	}
	
	@POST
	@Path("/register/{taskRepoId}")
	@ApiOperation(value="Register a new task based on the particular SHA1 from the task repo",response=Task.class)
	public Task register(@PathParam("taskRepoId") String taskRepoId, @FormParam("sha1") String sha1) throws IOException, InvalidTagFormatException, GitAPIException {
		String newUuid = taskManager.reserveNewTaskUuid();
		RegistrationTag r = taskRepoManager.recordRegistration(taskRepoId, sha1, newUuid, false /* disabled */);
		Task t = taskManager.cloneTask(r);
		return t;
	}
	
	@POST
	@Path("/test/{taskRepoId}")
	@ApiOperation(value="Check that the task repository at this SHA1 actually conforms to the specification", response=TaskTestResponse.class)
	public TaskTestResponse test(@PathParam("taskRepoId") String taskRepoId, @FormParam("sha1") String sha1) throws IOException, GitAPIException {
		return taskRepoManager.test(taskRepoId, sha1);
	}
}

