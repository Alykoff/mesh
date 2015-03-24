package com.gentics.cailun.core.verticle;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import io.vertx.core.http.HttpMethod;

import java.io.IOException;

import org.codehaus.jackson.JsonGenerationException;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gentics.cailun.core.AbstractRestVerticle;
import com.gentics.cailun.core.data.model.auth.PermissionType;
import com.gentics.cailun.core.data.model.auth.User;
import com.gentics.cailun.core.data.service.GroupService;
import com.gentics.cailun.core.data.service.UserService;
import com.gentics.cailun.core.rest.user.request.UserCreateRequest;
import com.gentics.cailun.core.rest.user.request.UserUpdateRequest;
import com.gentics.cailun.core.rest.user.response.UserResponse;
import com.gentics.cailun.test.AbstractRestVerticleTest;
import com.gentics.cailun.util.JsonUtils;

public class UserVerticleTest extends AbstractRestVerticleTest {

	@Autowired
	private UserVerticle userVerticle;

	@Autowired
	private UserService userService;

	@Autowired
	private GroupService groupService;

	@Override
	public AbstractRestVerticle getVerticle() {
		return userVerticle;
	}

	// Read Tests

	@Test
	public void testReadTagByUUID() throws Exception {
		User user = info.getUser();
		assertNotNull("The UUID of the user must not be null.", user.getUuid());

		roleService.addPermission(info.getRole(), user, PermissionType.READ);

		String response = request(info, HttpMethod.GET, "/api/v1/users/" + user.getUuid(), 200, "OK");
		String json = "{\"uuid\":\"uuid-value\",\"lastname\":\"Stark\",\"firstname\":\"Tony\",\"username\":\"dummy_user\",\"emailAddress\":\"t.stark@spam.gentics.com\",\"groups\":[\"dummy_user_group\"]}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);
	}

	@Test
	public void testReadByUUIDWithNoPermission() throws Exception {
		User user = info.getUser();
		assertNotNull("The username of the user must not be null.", user.getUsername());

		roleService.addPermission(info.getRole(), user, PermissionType.DELETE);
		roleService.addPermission(info.getRole(), user, PermissionType.CREATE);
		roleService.addPermission(info.getRole(), user, PermissionType.UPDATE);

		String response = request(info, HttpMethod.GET, "/api/v1/users/" + user.getUuid(), 403, "Forbidden");
		String json = "{\"message\":\"Missing permission on object {" + user.getUuid() + "}\"}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);
	}

	@Test
	public void testReadAllUsers() throws Exception {
		User user = info.getUser();

		User user2 = new User("testuser_2");
		user2.setLastname("A");
		user2.setFirstname("A");
		user2.setEmailAddress("test");
		user2 = userService.save(user2);
		info.getGroup().addUser(user2);

		User user3 = new User("testuser_3");
		user3.setLastname("should_not_be_listed");
		user3.setFirstname("should_not_be_listed");
		user3.setEmailAddress("should_not_be_listed");
		user3 = userService.save(user3);
		info.getGroup().addUser(user3);
		groupService.save(info.getGroup());

		assertNotNull(userService.findByUsername(user.getUsername()));
		roleService.addPermission(info.getRole(), user, PermissionType.READ);
		roleService.addPermission(info.getRole(), user2, PermissionType.READ);
		// Don't grant permissions to user3

		String response = request(info, HttpMethod.GET, "/api/v1/users/", 200, "OK");
		String json = "{\"dummy_user\":{\"uuid\":\"uuid-value\",\"lastname\":\"Stark\",\"firstname\":\"Tony\",\"username\":\"dummy_user\",\"emailAddress\":\"t.stark@spam.gentics.com\",\"groups\":[\"dummy_user_group\"]},\"testuser_2\":{\"uuid\":\"uuid-value\",\"lastname\":\"A\",\"firstname\":\"A\",\"username\":\"testuser_2\",\"emailAddress\":\"test\",\"groups\":[\"dummy_user_group\"]}}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);
	}

	// Update tests

	@Test
	public void testUpdateUser() throws Exception {
		User user = info.getUser();

		roleService.addPermission(info.getRole(), user, PermissionType.UPDATE);

		UserUpdateRequest restUser = new UserUpdateRequest();
		restUser.setUuid(user.getUuid());
		restUser.setEmailAddress("t.stark@stark-industries.com");
		restUser.setFirstname("Tony Awesome");
		restUser.setLastname("Epic Stark");
		restUser.setUsername("dummy_user_changed");

		String response = request(info, HttpMethod.PUT, "/api/v1/users/" + user.getUuid(), 200, "OK", JsonUtils.toJson(restUser));
		String json = "{\"uuid\":\"uuid-value\",\"lastname\":\"Epic Stark\",\"firstname\":\"Tony Awesome\",\"username\":\"dummy_user_changed\",\"emailAddress\":\"t.stark@stark-industries.com\",\"groups\":[\"dummy_user_group\"]}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);

		User reloadedUser = userService.findByUUID(user.getUuid());
		Assert.assertEquals("Epic Stark", reloadedUser.getLastname());
		Assert.assertEquals("Tony Awesome", reloadedUser.getFirstname());
		Assert.assertEquals("t.stark@stark-industries.com", reloadedUser.getEmailAddress());
		Assert.assertEquals("dummy_user_changed", reloadedUser.getUsername());
	}

	@Test
	public void testUpdatePassword() throws JsonGenerationException, JsonMappingException, IOException, Exception {
		User user = info.getUser();
		String oldHash = user.getPasswordHash();
		roleService.addPermission(info.getRole(), user, PermissionType.UPDATE);

		UserUpdateRequest restUser = new UserUpdateRequest();
		restUser.setPassword("new_password");

		String response = request(info, HttpMethod.PUT, "/api/v1/users/" + user.getUuid(), 200, "OK", new ObjectMapper().writeValueAsString(restUser));
		String json = "{\"uuid\":\"uuid-value\",\"lastname\":\"Stark\",\"firstname\":\"Tony\",\"username\":\"dummy_user\",\"emailAddress\":\"t.stark@spam.gentics.com\",\"groups\":[\"dummy_user_group\"]}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);

		User reloadedUser = userService.findByUUID(user.getUuid());
		assertTrue("The hash should be different and thus the password updated.", oldHash != reloadedUser.getPasswordHash());
		Assert.assertEquals(user.getUsername(), reloadedUser.getUsername());
		Assert.assertEquals(user.getFirstname(), reloadedUser.getFirstname());
		Assert.assertEquals(user.getLastname(), reloadedUser.getLastname());
		Assert.assertEquals(user.getEmailAddress(), reloadedUser.getEmailAddress());
	}

	@Test
	public void testUpdatePasswordWithNoPermission() throws JsonGenerationException, JsonMappingException, IOException, Exception {
		User user = info.getUser();
		String oldHash = user.getPasswordHash();
		roleService.addPermission(info.getRole(), user, PermissionType.READ);

		UserUpdateRequest restUser = new UserUpdateRequest();
		restUser.setPassword("new_password");

		String response = request(info, HttpMethod.PUT, "/api/v1/users/" + user.getUuid(), 403, "Forbidden",
				new ObjectMapper().writeValueAsString(restUser));
		String json = "{\"message\":\"Missing permission on object {" + user.getUuid() + "}\"}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);

		User reloadedUser = userService.findByUUID(user.getUuid());
		assertTrue("The hash should not be updated.", oldHash.equals(reloadedUser.getPasswordHash()));
	}

	@Test
	public void testUpdateUserWithNoPermission() throws Exception {
		User user = info.getUser();
		String oldHash = user.getPasswordHash();
		roleService.addPermission(info.getRole(), user, PermissionType.READ);

		UserResponse updatedUser = new UserResponse();
		updatedUser.setEmailAddress("n.user@spam.gentics.com");
		updatedUser.setFirstname("Joe");
		updatedUser.setLastname("Doe");
		updatedUser.setUsername("new_user");
		updatedUser.addGroup(info.getGroup().getName());

		String response = request(info, HttpMethod.PUT, "/api/v1/users/" + user.getUuid(), 403, "Forbidden",
				new ObjectMapper().writeValueAsString(updatedUser));
		String json = "{\"message\":\"Missing permission on object {" + user.getUuid() + "}\"}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);

		User reloadedUser = userService.findByUUID(user.getUuid());
		assertTrue("The hash should not be updated.", oldHash.equals(reloadedUser.getPasswordHash()));
		Assert.assertEquals("The firstname should not be updated.", user.getFirstname(), reloadedUser.getFirstname());
		Assert.assertEquals("The firstname should not be updated.", user.getLastname(), reloadedUser.getLastname());
	}

	@Test
	public void testUpdateUserWithConflictingUsername() throws Exception {
		User user = info.getUser();

		roleService.addPermission(info.getRole(), user, PermissionType.UPDATE);

		// Create an user with a conflicting username
		User conflictingUser = new User("existing_username");
		conflictingUser = userService.save(conflictingUser);
		info.getGroup().addUser(conflictingUser);

		UserUpdateRequest newUser = new UserUpdateRequest();
		newUser.setUsername("existing_username");
		newUser.setUuid(user.getUuid());

		String requestJson = new ObjectMapper().writeValueAsString(newUser);
		String response = request(info, HttpMethod.PUT, "/api/v1/users/" + user.getUuid(), 409, "Conflict", requestJson);
		String json = "{\"message\":\"A user with the username {existing_username} already exists. Please choose a different username.\"}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);

	}

	// Create tests

	@Test
	public void testCreateUserWithConflictingUsername() throws Exception {

		// Create an user with a conflicting username
		User conflictingUser = new User("existing_username");
		conflictingUser = userService.save(conflictingUser);
		info.getGroup().addUser(conflictingUser);

		// Add update permission to group in order to create the user in that group
		roleService.addPermission(info.getRole(), info.getGroup(), PermissionType.UPDATE);

		UserCreateRequest newUser = new UserCreateRequest();
		newUser.setUsername("existing_username");
		newUser.setGroupUuid(info.getGroup().getUuid());
		newUser.setPassword("test1234");

		String requestJson = new ObjectMapper().writeValueAsString(newUser);
		String response = request(info, HttpMethod.POST, "/api/v1/users/", 409, "Conflict", requestJson);
		String json = "{\"message\":\"Username is conflicting with an existing username.\"}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);

	}

	@Test
	public void testCreateUserWithNoPassword() throws Exception {
		// Add update permission to group in order to create the user in that group
		roleService.addPermission(info.getRole(), info.getGroup(), PermissionType.UPDATE);

		UserCreateRequest newUser = new UserCreateRequest();
		newUser.setEmailAddress("n.user@spam.gentics.com");
		newUser.setFirstname("Joe");
		newUser.setLastname("Doe");
		newUser.setUsername("new_user_test123");
		newUser.setGroupUuid(info.getGroup().getUuid());

		String requestJson = new ObjectMapper().writeValueAsString(newUser);
		String response = request(info, HttpMethod.POST, "/api/v1/users/", 400, "Bad Request", requestJson);
		String json = "{\"message\":\"No password was specified.\"}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);
	}

	@Test
	public void testCreateUserWithNoUsername() throws Exception {
		// Add update permission to group in order to create the user in that group
		roleService.addPermission(info.getRole(), info.getGroup(), PermissionType.UPDATE);

		UserCreateRequest newUser = new UserCreateRequest();
		newUser.setEmailAddress("n.user@spam.gentics.com");
		newUser.setFirstname("Joe");
		newUser.setLastname("Doe");
		newUser.setPassword("test123456");

		String requestJson = new ObjectMapper().writeValueAsString(newUser);
		String response = request(info, HttpMethod.POST, "/api/v1/users/", 400, "Bad Request", requestJson);
		String json = "{\"message\":\"No username was specified.\"}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);
	}

	@Test
	public void testCreateUserWithNoParentGroup() throws Exception {
		// Add update permission to group in order to create the user in that group
		roleService.addPermission(info.getRole(), info.getGroup(), PermissionType.UPDATE);

		UserCreateRequest newUser = new UserCreateRequest();
		newUser.setEmailAddress("n.user@spam.gentics.com");
		newUser.setFirstname("Joe");
		newUser.setLastname("Doe");
		newUser.setUsername("new_user");
		newUser.setPassword("test123456");

		String requestJson = new ObjectMapper().writeValueAsString(newUser);
		String response = request(info, HttpMethod.POST, "/api/v1/users/", 400, "Bad Request", requestJson);
		String json = "{\"message\":\"No parent group was specified for the user. Please set a parent group uuid.\"}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);

	}

	@Test
	public void testCreateUserWithBogusParentGroup() throws Exception {
		// Add update permission to group in order to create the user in that group
		roleService.addPermission(info.getRole(), info.getGroup(), PermissionType.UPDATE);

		UserCreateRequest newUser = new UserCreateRequest();
		newUser.setEmailAddress("n.user@spam.gentics.com");
		newUser.setFirstname("Joe");
		newUser.setLastname("Doe");
		newUser.setUsername("new_user");
		newUser.setPassword("test123456");
		newUser.setGroupUuid("bogus");

		String requestJson = new ObjectMapper().writeValueAsString(newUser);
		String response = request(info, HttpMethod.POST, "/api/v1/users/", 404, "Not Found", requestJson);
		String json = "{\"message\":\"Object with uuid \\\"bogus\\\" could not be found.\"}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);

	}

	@Test
	public void testCreateUser() throws Exception {

		// Add update permission to group in order to create the user in that group
		roleService.addPermission(info.getRole(), info.getGroup(), PermissionType.UPDATE);

		UserCreateRequest newUser = new UserCreateRequest();
		newUser.setEmailAddress("n.user@spam.gentics.com");
		newUser.setFirstname("Joe");
		newUser.setLastname("Doe");
		newUser.setUsername("new_user");
		newUser.setPassword("test123456");
		newUser.setGroupUuid(info.getGroup().getUuid());

		String requestJson = new ObjectMapper().writeValueAsString(newUser);
		String response = request(info, HttpMethod.POST, "/api/v1/users/", 200, "OK", requestJson);
		String json = "{\"uuid\":\"uuid-value\",\"lastname\":\"Doe\",\"firstname\":\"Joe\",\"username\":\"new_user\",\"emailAddress\":\"n.user@spam.gentics.com\",\"groups\":[\"dummy_user_group\"]}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);

	}

	@Test
	public void testCreateUserWithBogusJson() throws Exception {

		String requestJson = "bogus text";
		String response = request(info, HttpMethod.POST, "/api/v1/users/", 400, "Bad Request", requestJson);
		String json = "{\"message\":\"Could not parse request json.\"}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);
	}

	// Delete tests

	@Test
	public void testDeleteUserByUUID() throws Exception {
		User user = info.getUser();

		roleService.addPermission(info.getRole(), user, PermissionType.DELETE);

		String response = request(info, HttpMethod.DELETE, "/api/v1/users/" + user.getUuid(), 200, "OK");
		String json = "{\"message\":\"User with uuid \\\"" + user.getUuid() + "\\\" was deleted.\"}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);
		assertNull("The user should have been deleted", userService.findByUUID(user.getUuid()));
	}

	@Test
	public void testDeleteByUUIDWithNoPermission() throws Exception {
		User user = info.getUser();

		String response = request(info, HttpMethod.DELETE, "/api/v1/users/" + user.getUuid(), 403, "Forbidden");
		String json = "{\"message\":\"Missing permission on object {" + user.getUuid() + "}\"}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);
		assertNotNull("The user should not have been deleted", userService.findByUUID(user.getUuid()));
	}

}
