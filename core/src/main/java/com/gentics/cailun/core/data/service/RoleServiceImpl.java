package com.gentics.cailun.core.data.service;

import org.neo4j.graphdb.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.conversion.Result;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gentics.cailun.core.data.model.auth.GraphPermission;
import com.gentics.cailun.core.data.model.auth.Group;
import com.gentics.cailun.core.data.model.auth.PermissionType;
import com.gentics.cailun.core.data.model.auth.Role;
import com.gentics.cailun.core.data.model.generic.AbstractPersistable;
import com.gentics.cailun.core.data.service.generic.GenericNodeServiceImpl;
import com.gentics.cailun.core.repository.RoleRepository;
import com.gentics.cailun.core.rest.group.response.GroupResponse;
import com.gentics.cailun.core.rest.role.response.RoleResponse;
import com.gentics.cailun.error.HttpStatusCodeErrorException;

@Component
@Transactional
public class RoleServiceImpl extends GenericNodeServiceImpl<Role> implements RoleService {

	@Autowired
	RoleRepository roleRepository;

	@Override
	public Role findByUUID(String uuid) {
		return roleRepository.findByUUID(uuid);
	}

	@Override
	public Role findByName(String name) {
		return roleRepository.findByName(name);
	}

	@Override
	public Result<Role> findAll() {
		return roleRepository.findAll();
	}

	@Override
	public void addPermission(Role role, AbstractPersistable node, PermissionType... permissionTypes) {
		// TODO why is the @Transactional not working?
		try (Transaction tx = springConfig.getGraphDatabaseService().beginTx()) {
			GraphPermission permission = getGraphPermission(role, node);
			// Create a new permission relation when no existing one could be found
			if (permission == null) {
				permission = new GraphPermission(role, node);
			}
			for (int i = 0; i < permissionTypes.length; i++) {
				permission.grant(permissionTypes[i]);
			}
			role.addPermission(permission);
			role = save(role);
			tx.success();
		}
	}

	@Override
	public GraphPermission getGraphPermission(Role role, AbstractPersistable node) {
		return roleRepository.findPermission(role.getId(), node.getId());
	}

	@Override
	public GraphPermission revokePermission(Role role, AbstractPersistable node, PermissionType... permissionTypes) {
		try (Transaction tx = springConfig.getGraphDatabaseService().beginTx()) {
			GraphPermission permission = getGraphPermission(role, node);
			// Create a new permission relation when no existing one could be found
			if (permission == null) {
				return null;
			}
			for (int i = 0; i < permissionTypes.length; i++) {
				permission.revoke(permissionTypes[i]);
			}
			role.addPermission(permission);
			role = save(role);
			tx.success();
			return permission;
		}
	}

	@Override
	public RoleResponse transformToRest(Role role) {
		if (role == null) {
			throw new HttpStatusCodeErrorException(500, "Role can't be null");
		}
		RoleResponse restRole = new RoleResponse();
		restRole.setUuid(role.getUuid());
		restRole.setName(role.getName());

		for (Group group : role.getGroups()) {
			group = neo4jTemplate.fetch(group);
			GroupResponse restGroup = new GroupResponse();
			restGroup.setName(group.getName());
			restGroup.setUuid(group.getUuid());
			restRole.getGroups().add(restGroup);
		}

		return restRole;
	}

}
