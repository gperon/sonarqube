/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.db.permission;

import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.core.permission.ProjectPermissions;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.UserDto;

import static com.google.common.collect.Sets.newHashSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.core.permission.GlobalPermissions.QUALITY_GATE_ADMIN;
import static org.sonar.core.permission.GlobalPermissions.SCAN_EXECUTION;
import static org.sonar.core.permission.GlobalPermissions.SYSTEM_ADMIN;
import static org.sonar.db.permission.OrganizationPermission.ADMINISTER_QUALITY_GATES;
import static org.sonar.db.permission.OrganizationPermission.SCAN;

public class AuthorizationDaoTest {

  private static final Long PROJECT_ID = 300L;
  private static final int MISSING_ID = -1;
  private static final String A_PERMISSION = "a-permission";
  private static final String DOES_NOT_EXIST = "does-not-exist";

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);

  private DbSession dbSession = db.getSession();
  private AuthorizationDao underTest = new AuthorizationDao();
  private OrganizationDto organization;
  private UserDto user;
  private GroupDto group1;
  private GroupDto group2;
  private Set<Integer> randomExistingUserIds;
  private String randomPermission = "p" + new Random().nextInt();

  @Before
  public void setUp() throws Exception {
    organization = db.organizations().insert();
    user = db.users().insertUser();
    group1 = db.users().insertGroup(organization, "group1");
    group2 = db.users().insertGroup(organization, "group2");
    randomExistingUserIds = IntStream.range(0, 1 + Math.abs(new Random().nextInt(5)))
      .map(i -> db.users().insertUser().getId())
      .boxed()
      .collect(MoreCollectors.toSet());
  }

  /**
   * Union of the permissions granted to:
   * - the user
   * - the groups which user is member
   * - anyone
   */
  @Test
  public void selectOrganizationPermissions_for_logged_in_user() {
    ComponentDto project = db.components().insertPrivateProject(organization);
    db.users().insertMember(group1, user);
    db.users().insertPermissionOnUser(organization, user, "perm1");
    db.users().insertProjectPermissionOnUser(user, "perm42", project);
    db.users().insertPermissionOnGroup(group1, "perm2");
    db.users().insertPermissionOnAnyone(organization, "perm3");

    // ignored permissions, user is not member of this group
    db.users().insertPermissionOnGroup(group2, "ignored");

    Set<String> permissions = underTest.selectOrganizationPermissions(dbSession, organization.getUuid(), user.getId());

    assertThat(permissions).containsOnly("perm1", "perm2", "perm3");
  }

  /**
   * Anonymous user only benefits from the permissions granted to
   * "Anyone"
   */
  @Test
  public void selectOrganizationPermissions_for_anonymous_user() {
    db.users().insertPermissionOnAnyone(organization, "perm1");

    // ignored permissions
    db.users().insertPermissionOnUser(organization, user, "ignored");
    db.users().insertPermissionOnGroup(group1, "ignored");

    Set<String> permissions = underTest.selectOrganizationPermissionsOfAnonymous(dbSession, organization.getUuid());

    assertThat(permissions).containsOnly("perm1");
  }

  @Test
  public void user_should_be_authorized() {
    ComponentDto project1 = db.components().insertPublicProject(organization);
    ComponentDto project2 = db.components().insertPublicProject(organization);
    ComponentDto project3 = db.components().insertPublicProject(organization);
    UserDto user = db.users().insertUser("u1");
    GroupDto group = db.users().insertGroup(organization);
    db.users().insertProjectPermissionOnUser(user, UserRole.USER, project2);
    db.users().insertProjectPermissionOnUser(user, UserRole.USER, project3);
    db.users().insertMember(group, user);
    db.users().insertProjectPermissionOnGroup(group, UserRole.USER, project1);

    assertThat(underTest.keepAuthorizedProjectIds(dbSession, newHashSet(project2.getId(), project3.getId()), user.getId(), UserRole.USER))
      .containsOnly(project2.getId(), project3.getId());

    // user does not have the role "admin"
    assertThat(underTest.keepAuthorizedProjectIds(dbSession, newHashSet(project2.getId()), user.getId(), UserRole.ADMIN))
      .isEmpty();

    assertThat(underTest.keepAuthorizedProjectIds(dbSession, Collections.emptySet(), user.getId(), UserRole.ADMIN))
      .isEmpty();
  }

  @Test
  public void countUsersWithGlobalPermissionExcludingGroup() {
    // users with global permission "perm1" :
    // - "u1" and "u2" through group "g1"
    // - "u1" and "u3" through group "g2"
    // - "u4"

    UserDto user1 = db.users().insertUser();
    UserDto user2 = db.users().insertUser();
    UserDto user3 = db.users().insertUser();
    UserDto user4 = db.users().insertUser();
    UserDto user5 = db.users().insertUser();

    OrganizationDto organization = db.organizations().insert();
    GroupDto group1 = db.users().insertGroup(organization, "g1");
    db.users().insertPermissionOnGroup(group1, "perm1");
    db.users().insertPermissionOnGroup(group1, "perm2");
    db.users().insertMember(group1, user1);
    db.users().insertMember(group1, user2);

    GroupDto group2 = db.users().insertGroup(organization, "g2");
    db.users().insertPermissionOnGroup(group2, "perm1");
    db.users().insertPermissionOnGroup(group2, "perm2");
    db.users().insertMember(group2, user1);
    db.users().insertMember(group2, user3);

    // group3 has the permission "perm1" but has no users
    GroupDto group3 = db.users().insertGroup(organization, "g2");
    db.users().insertPermissionOnGroup(group3, "perm1");

    db.users().insertPermissionOnUser(organization, user4, "perm1");
    db.users().insertPermissionOnUser(organization, user4, "perm2");
    db.users().insertPermissionOnAnyone(organization, "perm1");

    // other organizations are ignored
    OrganizationDto org2 = db.organizations().insert();
    db.users().insertPermissionOnUser(org2, user1, "perm1");

    // excluding group "g1" -> remain u1, u3 and u4
    assertThat(underTest.countUsersWithGlobalPermissionExcludingGroup(db.getSession(),
      organization.getUuid(), "perm1", group1.getId())).isEqualTo(3);

    // excluding group "g2" -> remain u1, u2 and u4
    assertThat(underTest.countUsersWithGlobalPermissionExcludingGroup(db.getSession(),
      organization.getUuid(), "perm1", group2.getId())).isEqualTo(3);

    // excluding group "g3" -> remain u1, u2, u3 and u4
    assertThat(underTest.countUsersWithGlobalPermissionExcludingGroup(db.getSession(),
      organization.getUuid(), "perm1", group3.getId())).isEqualTo(4);

    // nobody has the permission
    assertThat(underTest.countUsersWithGlobalPermissionExcludingGroup(db.getSession(),
      organization.getUuid(), "missingPermission", group1.getId())).isEqualTo(0);
  }

  @Test
  public void countUsersWithGlobalPermissionExcludingUser() {
    // group g1 has the permission p1 and has members user1 and user2
    // user3 has the permission
    UserDto user1 = db.users().insertUser();
    UserDto user2 = db.users().insertUser();
    UserDto user3 = db.users().insertUser();

    OrganizationDto organization = db.organizations().insert();
    GroupDto group1 = db.users().insertGroup(organization, "g1");
    db.users().insertPermissionOnGroup(group1, "p1");
    db.users().insertPermissionOnGroup(group1, "p2");
    db.users().insertMember(group1, user1);
    db.users().insertMember(group1, user2);
    db.users().insertPermissionOnUser(organization, user3, "p1");
    db.users().insertPermissionOnAnyone(organization, "p1");

    // other organizations are ignored
    OrganizationDto org2 = db.organizations().insert();
    db.users().insertPermissionOnUser(org2, user1, "p1");

    // excluding user1 -> remain user2 and user3
    assertThat(underTest.countUsersWithGlobalPermissionExcludingUser(db.getSession(),
      organization.getUuid(), "p1", user1.getId())).isEqualTo(2);

    // excluding user3 -> remain the members of group g1
    assertThat(underTest.countUsersWithGlobalPermissionExcludingUser(db.getSession(),
      organization.getUuid(), "p1", user3.getId())).isEqualTo(2);

    // excluding unknown user
    assertThat(underTest.countUsersWithGlobalPermissionExcludingUser(db.getSession(),
      organization.getUuid(), "p1", -1)).isEqualTo(3);

    // nobody has the permission
    assertThat(underTest.countUsersWithGlobalPermissionExcludingUser(db.getSession(),
      organization.getUuid(), "missingPermission", group1.getId())).isEqualTo(0);
  }

  @Test
  public void keep_authorized_project_ids_for_user() {
    ComponentDto project1 = db.components().insertPublicProject(organization);
    ComponentDto project2 = db.components().insertPublicProject(organization);
    UserDto user1 = db.users().insertUser("u1");
    db.users().insertProjectPermissionOnUser(user1, UserRole.USER, project1);
    db.users().insertProjectPermissionOnUser(user1, UserRole.CODEVIEWER, project2);

    assertThat(underTest.keepAuthorizedProjectIds(dbSession, newHashSet(project1.getId(), project2.getId()), user1.getId(), UserRole.USER))
      .containsOnly(project1.getId());

    // user does not have the role "admin"
    assertThat(underTest.keepAuthorizedProjectIds(dbSession, newHashSet(project1.getId()), user1.getId(), UserRole.ADMIN)).isEmpty();

    // Empty list
    assertThat(underTest.keepAuthorizedProjectIds(dbSession, Collections.emptySet(), user1.getId(), UserRole.ADMIN)).isEmpty();
  }

  @Test
  public void keep_authorized_project_ids_for_group() {
    ComponentDto project1 = db.components().insertPublicProject(organization);
    ComponentDto project2 = db.components().insertPublicProject(organization);
    UserDto user1 = db.users().insertUser("u1");
    GroupDto group = db.users().insertGroup(organization);
    db.users().insertMembers(group, user1);
    db.users().insertProjectPermissionOnGroup(group, UserRole.USER, project1);
    db.users().insertProjectPermissionOnGroup(group, UserRole.CODEVIEWER, project2);

    assertThat(underTest.keepAuthorizedProjectIds(dbSession, newHashSet(project1.getId(), project2.getId()), user1.getId(), UserRole.USER))
      .containsOnly(project1.getId());

    // user does not have the role "admin"
    assertThat(underTest.keepAuthorizedProjectIds(dbSession, newHashSet(project1.getId()), user1.getId(), UserRole.ADMIN))
      .isEmpty();

    // Empty list
    assertThat(underTest.keepAuthorizedProjectIds(dbSession, Collections.emptySet(), user1.getId(), UserRole.ADMIN))
      .isEmpty();
  }

  @Test
  public void keep_authorized_project_ids_for_anonymous() {
    ComponentDto project1 = db.components().insertPublicProject(organization);
    ComponentDto project2 = db.components().insertPublicProject(organization);
    UserDto user1 = db.users().insertUser("u1");
    GroupDto group = db.users().insertGroup(organization);
    db.users().insertMembers(group, user1);
    db.users().insertProjectPermissionOnAnyone(UserRole.USER, project1);
    db.users().insertProjectPermissionOnGroup(group, UserRole.CODEVIEWER, project2);

    assertThat(underTest.keepAuthorizedProjectIds(dbSession, newHashSet(project1.getId(), project2.getId()), null, UserRole.USER))
      .containsOnly(project1.getId());

    // user does not have the role "admin"
    assertThat(underTest.keepAuthorizedProjectIds(dbSession, newHashSet(project1.getId()), null, UserRole.ADMIN))
      .isEmpty();

    // Empty list
    assertThat(underTest.keepAuthorizedProjectIds(dbSession, Collections.emptySet(), null, UserRole.ADMIN))
      .isEmpty();
  }

  @Test
  public void group_should_be_authorized() {
    ComponentDto project1 = db.components().insertPublicProject(organization);
    ComponentDto project2 = db.components().insertPublicProject(organization);
    ComponentDto project3 = db.components().insertPublicProject(organization);
    UserDto user1 = db.users().insertUser("u1");
    GroupDto group = db.users().insertGroup(organization);
    db.users().insertMembers(group, user1);
    db.users().insertProjectPermissionOnUser(user1, UserRole.USER, project1);
    db.users().insertProjectPermissionOnGroup(group, UserRole.USER, project2);
    db.users().insertProjectPermissionOnGroup(group, UserRole.USER, project3);

    assertThat(underTest.keepAuthorizedProjectIds(dbSession, newHashSet(project2.getId(), project3.getId()), user1.getId(), UserRole.USER))
      .containsOnly(project2.getId(), project3.getId());

    // group does not have the role "admin"
    assertThat(underTest.keepAuthorizedProjectIds(dbSession, newHashSet(project2.getId(), project3.getId()), user1.getId(), UserRole.ADMIN))
      .isEmpty();
  }

  @Test
  public void anonymous_should_be_authorized() {
    ComponentDto project1 = db.components().insertPublicProject(organization);
    ComponentDto project2 = db.components().insertPublicProject(organization);
    UserDto user1 = db.users().insertUser("u1");
    GroupDto group = db.users().insertGroup(organization);
    db.users().insertMembers(group, user1);
    db.users().insertProjectPermissionOnAnyone(UserRole.USER, project1);
    db.users().insertProjectPermissionOnAnyone(UserRole.USER, project2);

    assertThat(underTest.keepAuthorizedProjectIds(dbSession, newHashSet(project1.getId(), project2.getId()), null, UserRole.USER))
      .containsOnly(project1.getId(), project2.getId());

    // group does not have the role "admin"
    assertThat(underTest.keepAuthorizedProjectIds(dbSession, newHashSet(project1.getId()), null, "admin"))
      .isEmpty();
  }

  @Test
  public void keepAuthorizedUsersForRoleAndProject_returns_empty_if_user_set_is_empty_on_public_project() {
    OrganizationDto organization = db.organizations().insert();
    ComponentDto project = db.components().insertPublicProject(organization);

    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.emptySet(), UserRole.USER, project.getId()))
      .isEmpty();
  }

  @Test
  public void keepAuthorizedUsersForRoleAndProject_returns_empty_for_non_existent_users() {
    ComponentDto project = new Random().nextBoolean() ? db.components().insertPublicProject(organization) : db.components().insertPrivateProject(organization);
    Set<Integer> randomNonExistingUserIdsSet = IntStream.range(0, 1 + Math.abs(new Random().nextInt(5)))
      .map(i -> i + 1_990)
      .boxed()
      .collect(MoreCollectors.toSet());

    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, randomNonExistingUserIdsSet, UserRole.USER, project.getId()))
      .isEmpty();
  }

  @Test
  public void keepAuthorizedUsersForRoleAndProject_returns_any_users_for_public_project_without_any_permission_in_DB_and_permission_USER() {
    ComponentDto project = db.components().insertPublicProject(organization);

    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, randomExistingUserIds, UserRole.USER, project.getId()))
      .containsAll(randomExistingUserIds);
  }

  @Test
  public void keepAuthorizedUsersForRoleAndProject_returns_any_users_for_public_project_without_any_permission_in_DB_and_permission_CODEVIEWER() {
    ComponentDto project = db.components().insertPublicProject(organization);

    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, randomExistingUserIds, UserRole.CODEVIEWER, project.getId()))
      .containsAll(randomExistingUserIds);
  }

  @Test
  public void keepAuthorizedUsersForRoleAndProject_returns_empty_for_any_users_on_public_project_without_any_permission_in_DB() {
    ComponentDto project = db.components().insertPublicProject(organization);

    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, randomExistingUserIds, randomPermission, project.getId()))
      .isEmpty();
  }

  @Test
  public void keepAuthorizedUsersForRoleAndProject_returns_user_if_granted_project_permission_directly_on_public_project() {
    ComponentDto project = db.components().insertPublicProject(organization);
    ComponentDto otherProject = db.components().insertPublicProject(organization);
    UserDto otherUser = db.users().insertUser();
    db.users().insertProjectPermissionOnUser(user, randomPermission, project);

    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(user.getId()), randomPermission, project.getId()))
      .containsOnly(user.getId());
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(user.getId()), "another perm", project.getId()))
      .isEmpty();
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(otherUser.getId()), randomPermission, project.getId()))
      .isEmpty();
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(user.getId()), randomPermission, otherProject.getId()))
      .isEmpty();
  }

  @Test
  public void keepAuthorizedUsersForRoleAndProject_returns_user_if_granted_project_permission_by_group_on_public_project() {
    ComponentDto project = db.components().insertPublicProject(organization);
    ComponentDto otherProject = db.components().insertPublicProject(organization);
    UserDto otherUser = db.users().insertUser();
    db.users().insertMember(group1, user);
    db.users().insertProjectPermissionOnGroup(group1, randomPermission, project);

    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(user.getId()), randomPermission, project.getId()))
      .containsOnly(user.getId());
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(user.getId()), "another perm", project.getId()))
      .isEmpty();
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(user.getId()), randomPermission, otherProject.getId()))
      .isEmpty();
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(otherUser.getId()), randomPermission, project.getId()))
      .isEmpty();
  }

  @Test
  public void keepAuthorizedUsersForRoleAndProject_does_not_return_user_if_granted_project_permission_by_AnyOne_on_public_project() {
    ComponentDto project = db.components().insertPublicProject(organization);
    ComponentDto otherProject = db.components().insertPublicProject(organization);
    UserDto otherUser = db.users().insertUser();
    db.users().insertProjectPermissionOnAnyone(randomPermission, project);

    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(user.getId()), randomPermission, project.getId()))
      .isEmpty();
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(user.getId()), "another perm", project.getId()))
      .isEmpty();
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(user.getId()), randomPermission, otherProject.getId()))
      .isEmpty();
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(otherUser.getId()), randomPermission, project.getId()))
      .isEmpty();
  }

  @Test
  public void keepAuthorizedUsersForRoleAndProject_returns_empty_for_any_user_on_private_project_without_any_permission_in_DB_and_permission_USER() {
    ComponentDto project = db.components().insertPrivateProject(organization);

    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, randomExistingUserIds, UserRole.USER, project.getId()))
      .isEmpty();
  }

  @Test
  public void keepAuthorizedUsersForRoleAndProject_returns_empty_for_any_user_on_private_project_without_any_permission_in_DB_and_permission_CODEVIEWER() {
    ComponentDto project = db.components().insertPrivateProject(organization);

    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, randomExistingUserIds, UserRole.CODEVIEWER, project.getId()))
      .isEmpty();
  }

  @Test
  public void keepAuthorizedUsersForRoleAndProject_returns_empty_for_any_users_and_any_permission_on_private_project_without_any_permission_in_DB() {
    ComponentDto project = db.components().insertPrivateProject(organization);

    ProjectPermissions.ALL
      .forEach(perm -> {
        assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, randomExistingUserIds, perm, project.getId()))
          .isEmpty();
      });
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, randomExistingUserIds, randomPermission, project.getId()))
      .isEmpty();
  }

  @Test
  public void keepAuthorizedUsersForRoleAndProject_returns_user_if_granted_project_permission_directly_on_private_project() {
    ComponentDto project = db.components().insertPrivateProject(organization);
    ComponentDto otherProject = db.components().insertPublicProject(organization);
    UserDto otherUser = db.users().insertUser();
    db.users().insertProjectPermissionOnUser(user, randomPermission, project);

    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(user.getId()), randomPermission, project.getId()))
      .containsOnly(user.getId());
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(user.getId()), "another perm", project.getId()))
      .isEmpty();
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(otherUser.getId()), randomPermission, project.getId()))
      .isEmpty();
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(user.getId()), randomPermission, otherProject.getId()))
      .isEmpty();
  }

  @Test
  public void keepAuthorizedUsersForRoleAndProject_returns_user_if_granted_project_permission_by_group_on_private_project() {
    ComponentDto project = db.components().insertPrivateProject(organization);
    ComponentDto otherProject = db.components().insertPublicProject(organization);
    UserDto otherUser = db.users().insertUser();
    db.users().insertMember(group1, user);
    db.users().insertProjectPermissionOnGroup(group1, randomPermission, project);

    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(user.getId()), randomPermission, project.getId()))
      .containsOnly(user.getId());
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(user.getId()), "another perm", project.getId()))
      .isEmpty();
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(user.getId()), randomPermission, otherProject.getId()))
      .isEmpty();
    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession, Collections.singleton(otherUser.getId()), randomPermission, project.getId()))
      .isEmpty();
  }

  @Test
  public void keep_authorized_users_returns_empty_list_for_role_and_project_for_anonymous() {
    ComponentDto project1 = db.components().insertPublicProject(organization);
    ComponentDto project2 = db.components().insertPublicProject(organization);
    ComponentDto project3 = db.components().insertPublicProject(organization);
    UserDto user1 = db.users().insertUser("u1");
    UserDto user2 = db.users().insertUser("u2");
    UserDto user3 = db.users().insertUser("u3");
    GroupDto group1 = db.users().insertGroup(organization);
    GroupDto group2 = db.users().insertGroup(organization);
    db.users().insertMembers(group1, user1, user2);
    db.users().insertMembers(group2, user3);
    db.users().insertProjectPermissionOnUser(user1, UserRole.USER, project1);
    db.users().insertProjectPermissionOnUser(user2, UserRole.USER, project1);
    db.users().insertProjectPermissionOnUser(user3, UserRole.USER, project1);
    db.users().insertProjectPermissionOnAnyone(UserRole.USER, project2);
    db.users().insertProjectPermissionOnGroup(group2, UserRole.USER, project3);

    assertThat(underTest.keepAuthorizedUsersForRoleAndProject(dbSession,
      // Only 100 and 101 has 'user' role on project
      newHashSet(100, 101, 102), "user", PROJECT_ID)).isEmpty();
  }

  @Test
  public void countUsersWithGlobalPermissionExcludingGroupMember() {
    // u1 has the direct permission, u2 and u3 have the permission through their group
    UserDto u1 = db.users().insertUser();
    db.users().insertPermissionOnUser(organization, u1, A_PERMISSION);
    db.users().insertPermissionOnGroup(group1, A_PERMISSION);
    db.users().insertPermissionOnGroup(group1, "another-permission");
    UserDto u2 = db.users().insertUser();
    db.users().insertMember(group1, u2);
    UserDto u3 = db.users().insertUser();
    db.users().insertMember(group1, u3);

    // excluding u2 membership --> remain u1 and u3
    int count = underTest.countUsersWithGlobalPermissionExcludingGroupMember(dbSession, organization.getUuid(), A_PERMISSION, group1.getId(), u2.getId());
    assertThat(count).isEqualTo(2);

    // excluding unknown memberships
    count = underTest.countUsersWithGlobalPermissionExcludingGroupMember(dbSession, organization.getUuid(), A_PERMISSION, group1.getId(), MISSING_ID);
    assertThat(count).isEqualTo(3);
    count = underTest.countUsersWithGlobalPermissionExcludingGroupMember(dbSession, organization.getUuid(), A_PERMISSION, MISSING_ID, u2.getId());
    assertThat(count).isEqualTo(3);

    // another organization
    count = underTest.countUsersWithGlobalPermissionExcludingGroupMember(dbSession, DOES_NOT_EXIST, A_PERMISSION, group1.getId(), u2.getId());
    assertThat(count).isEqualTo(0);

    // another permission
    count = underTest.countUsersWithGlobalPermissionExcludingGroupMember(dbSession, organization.getUuid(), DOES_NOT_EXIST, group1.getId(), u2.getId());
    assertThat(count).isEqualTo(0);
  }

  @Test
  public void countUsersWithGlobalPermissionExcludingUserPermission() {
    // u1 and u2 have the direct permission, u3 has the permission through his group
    UserDto u1 = db.users().insertUser();
    db.users().insertPermissionOnUser(organization, u1, A_PERMISSION);
    UserDto u2 = db.users().insertUser();
    db.users().insertPermissionOnUser(organization, u2, A_PERMISSION);
    db.users().insertPermissionOnGroup(group1, A_PERMISSION);
    UserDto u3 = db.users().insertUser();
    db.users().insertMember(group1, u3);

    // excluding u2 permission --> remain u1 and u3
    int count = underTest.countUsersWithGlobalPermissionExcludingUserPermission(dbSession, organization.getUuid(), A_PERMISSION, u2.getId());
    assertThat(count).isEqualTo(2);

    // excluding unknown user
    count = underTest.countUsersWithGlobalPermissionExcludingUserPermission(dbSession, organization.getUuid(), A_PERMISSION, MISSING_ID);
    assertThat(count).isEqualTo(3);

    // another organization
    count = underTest.countUsersWithGlobalPermissionExcludingUserPermission(dbSession, DOES_NOT_EXIST, A_PERMISSION, u2.getId());
    assertThat(count).isEqualTo(0);

    // another permission
    count = underTest.countUsersWithGlobalPermissionExcludingUserPermission(dbSession, organization.getUuid(), DOES_NOT_EXIST, u2.getId());
    assertThat(count).isEqualTo(0);
  }

  @Test
  public void selectOrganizationUuidsOfUserWithGlobalPermission_returns_empty_set_if_user_does_not_exist() {
    // another user
    db.users().insertPermissionOnUser(user, ADMINISTER_QUALITY_GATES);

    Set<String> orgUuids = underTest.selectOrganizationUuidsOfUserWithGlobalPermission(dbSession, MISSING_ID, SYSTEM_ADMIN);

    assertThat(orgUuids).isEmpty();
  }

  @Test
  public void selectOrganizationUuidsOfUserWithGlobalPermission_returns_empty_set_if_user_does_not_have_permission_at_all() {
    db.users().insertPermissionOnUser(user, ADMINISTER_QUALITY_GATES);
    // user is not part of this group
    db.users().insertPermissionOnGroup(group1, SCAN_EXECUTION);

    Set<String> orgUuids = underTest.selectOrganizationUuidsOfUserWithGlobalPermission(dbSession, user.getId(), SCAN_EXECUTION);

    assertThat(orgUuids).isEmpty();
  }

  @Test
  public void selectOrganizationUuidsOfUserWithGlobalPermission_returns_organizations_on_which_user_has_permission() {
    db.users().insertPermissionOnGroup(group1, SCAN_EXECUTION);
    db.users().insertPermissionOnGroup(group2, QUALITY_GATE_ADMIN);
    db.users().insertMember(group1, user);
    db.users().insertMember(group2, user);

    Set<String> orgUuids = underTest.selectOrganizationUuidsOfUserWithGlobalPermission(dbSession, user.getId(), SCAN_EXECUTION);

    assertThat(orgUuids).containsExactly(group1.getOrganizationUuid());
  }

  @Test
  public void selectOrganizationUuidsOfUserWithGlobalPermission_handles_user_permissions_and_group_permissions() {
    // organization: through group membership
    db.users().insertPermissionOnGroup(group1, SCAN_EXECUTION);
    db.users().insertMember(group1, user);

    // org2 : direct user permission
    OrganizationDto org2 = db.organizations().insert();
    db.users().insertPermissionOnUser(org2, user, SCAN_EXECUTION);

    // org3 : another permission QUALITY_GATE_ADMIN
    OrganizationDto org3 = db.organizations().insert();
    db.users().insertPermissionOnUser(org3, user, QUALITY_GATE_ADMIN);

    // exclude project permission
    db.users().insertProjectPermissionOnUser(user, UserRole.ADMIN, db.components().insertPrivateProject());

    Set<String> orgUuids = underTest.selectOrganizationUuidsOfUserWithGlobalPermission(dbSession, user.getId(), SCAN_EXECUTION);

    assertThat(orgUuids).containsOnly(organization.getUuid(), org2.getUuid());
  }

  @Test
  public void selectOrganizationUuidsOfUserWithGlobalPermission_ignores_anonymous_permissions() {
    db.users().insertPermissionOnAnyone(organization, SCAN);
    db.users().insertPermissionOnUser(organization, user, ADMINISTER_QUALITY_GATES);

    Set<String> orgUuids = underTest.selectOrganizationUuidsOfUserWithGlobalPermission(dbSession, user.getId(), SCAN.getKey());

    assertThat(orgUuids).isEmpty();
  }

  @Test
  public void selectProjectPermissionsOfAnonymous_returns_permissions_of_anonymous_user_on_specified_public_project() {
    ComponentDto project = db.components().insertPublicProject(organization);
    db.users().insertProjectPermissionOnAnyone(UserRole.CODEVIEWER, project);
    db.users().insertProjectPermissionOnUser(db.users().insertUser(), UserRole.USER, project);
    ComponentDto otherProject = db.components().insertPublicProject();
    db.users().insertProjectPermissionOnAnyone(UserRole.ISSUE_ADMIN, otherProject);

    assertThat(underTest.selectProjectPermissionsOfAnonymous(dbSession, project.uuid())).containsOnly(UserRole.CODEVIEWER);
  }

  @Test
  public void selectProjectPermissionsOfAnonymous_returns_empty_set_when_project_does_not_exist() {
    assertThat(underTest.selectProjectPermissionsOfAnonymous(dbSession, "does_not_exist")).isEmpty();
  }

  @Test
  public void selectProjectPermissions_returns_empty_set_when_logged_in_user_and_project_does_not_exist() {
    assertThat(underTest.selectProjectPermissions(dbSession, "does_not_exist", user.getId())).isEmpty();
  }

  @Test
  public void selectProjectPermissions_returns_permissions_of_logged_in_user_on_specified_public_project_through_anonymous_permissions() {
    ComponentDto project = db.components().insertPublicProject(organization);
    db.users().insertProjectPermissionOnAnyone(UserRole.CODEVIEWER, project);
    db.users().insertProjectPermissionOnAnyone(UserRole.ISSUE_ADMIN, project);

    assertThat(underTest.selectProjectPermissions(dbSession, project.uuid(), user.getId())).containsOnly(UserRole.CODEVIEWER, UserRole.ISSUE_ADMIN);
  }

  @Test
  public void selectProjectPermissions_returns_permissions_of_logged_in_user_on_specified_project() {
    ComponentDto project = db.components().insertPrivateProject(organization);
    db.users().insertProjectPermissionOnUser(user, UserRole.CODEVIEWER, project);
    db.users().insertProjectPermissionOnUser(db.users().insertUser(), UserRole.ISSUE_ADMIN, project);

    assertThat(underTest.selectProjectPermissions(dbSession, project.uuid(), user.getId())).containsOnly(UserRole.CODEVIEWER);
  }

  @Test
  public void selectProjectPermissions_returns_permissions_of_logged_in_user_on_specified_project_through_group_membership() {
    ComponentDto project = db.components().insertPrivateProject(organization);
    db.users().insertProjectPermissionOnGroup(group1, UserRole.CODEVIEWER, project);
    db.users().insertProjectPermissionOnGroup(group2, UserRole.ISSUE_ADMIN, project);
    db.users().insertMember(group1, user);

    assertThat(underTest.selectProjectPermissions(dbSession, project.uuid(), user.getId())).containsOnly(UserRole.CODEVIEWER);
  }

  @Test
  public void selectProjectPermissions_returns_permissions_of_logged_in_user_on_specified_private_project_through_all_possible_configurations() {
    ComponentDto project = db.components().insertPrivateProject(organization);
    db.users().insertProjectPermissionOnUser(user, UserRole.CODEVIEWER, project);
    db.users().insertProjectPermissionOnGroup(group1, UserRole.USER, project);
    db.users().insertMember(group1, user);

    assertThat(underTest.selectProjectPermissions(dbSession, project.uuid(), user.getId())).containsOnly(UserRole.CODEVIEWER, UserRole.USER);
  }

  @Test
  public void selectProjectPermissions_returns_permissions_of_logged_in_user_on_specified_public_project_through_all_possible_configurations() {
    ComponentDto project = db.components().insertPublicProject(organization);
    db.users().insertProjectPermissionOnUser(user, UserRole.CODEVIEWER, project);
    db.users().insertProjectPermissionOnAnyone(UserRole.ISSUE_ADMIN, project);
    db.users().insertProjectPermissionOnGroup(group1, UserRole.USER, project);
    db.users().insertMember(group1, user);

    assertThat(underTest.selectProjectPermissions(dbSession, project.uuid(), user.getId())).containsOnly(UserRole.CODEVIEWER, UserRole.ISSUE_ADMIN, UserRole.USER);
  }
}
