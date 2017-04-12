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
package org.sonar.server.platform.db.migration.version.v64;

import java.sql.SQLException;
import org.sonar.db.Database;
import org.sonar.server.platform.db.migration.step.DataChange;
import org.sonar.server.platform.db.migration.step.MassUpdate;
import org.sonar.server.platform.db.migration.step.Select;
import org.sonar.server.platform.db.migration.step.SqlStatement;

/**
 * This DB migration assumes the whole PROJECTS table contains only rows with value false
 * (set by {@link PopulateColumnProjectsPrivate}) and performs the following:
 * <ul>
 *   <li>set private=true for any tree of component which root has neither user nor codeviewer permission for group AnyOne</li>
 *   <li>removes any permission to group AnyOne for root components which are made private</li>
 *   <li>deletes any permission user or codeviewer for root components which stays public</li>
 * </ul>
 */
public class MakeComponentsPrivateBaseOnPermissions extends DataChange {
  public MakeComponentsPrivateBaseOnPermissions(Database db) {
    super(db);
  }

  @Override
  protected void execute(Context context) throws SQLException {
    makePrivateComponent(context);
    cleanPermissionsOfPublicComponents(context);
  }

  private static void makePrivateComponent(Context context) throws SQLException {
    MassUpdate massUpdate = context.prepareMassUpdate();
    massUpdate.select("select uuid, id from projects p where " +
      " p.scope = 'PRJ'" +
      " and p.qualifier in ('TRK', 'VW')" +
      " and p.private = ?" +
      " and not exists (" +
      "   select" +
      "     1" +
      "   from group_roles gr" +
      "   where " +
      "     gr.resource_id = p.id" +
      "     and gr.group_id is null" +
      "     and gr.role in ('user', 'codeviewer')" +
      " )")
      .setBoolean(1, false);
    massUpdate.rowPluralName("component trees to be made private");
    // make project private
    massUpdate.update("update projects set private = ? where project_uuid = ?");
    // delete any permission given to group "Anyone"
    massUpdate.update("delete from group_roles where resource_id = ? and group_id is null");
    massUpdate.execute(MakeComponentsPrivateBaseOnPermissions::handleMakePrivateComponent);
  }

  private static boolean handleMakePrivateComponent(Select.Row row, SqlStatement update, int updateIndex) throws SQLException {
    String rootUuid = row.getString(1);
    long id = row.getLong(2);
    switch (updateIndex) {
      case 0:
        update.setBoolean(1, true);
        update.setString(2, rootUuid);
        return true;
      case 1:
        update.setLong(1, id);
        return true;
      default:
        throw new IllegalArgumentException("Unsupported update index " + updateIndex);
    }
  }

  private static void cleanPermissionsOfPublicComponents(Context context) throws SQLException {
    MassUpdate massUpdate = context.prepareMassUpdate();
    massUpdate.select("select id from projects p where " +
      " p.scope = 'PRJ'" +
      " and p.qualifier in ('TRK', 'VW')" +
      " and p.private = ?" +
      " and exists (" +
      "   select" +
      "     1" +
      "   from group_roles gr" +
      "   where " +
      "     gr.resource_id = p.id" +
      "     and gr.role in ('user', 'codeviewer')" +
      "   union" +
      "   select" +
      "     1" +
      "   from user_roles gr" +
      "   where " +
      "     gr.resource_id = p.id" +
      "     and gr.role in ('user', 'codeviewer')" +
      ")")
      .setBoolean(1, false);
    massUpdate.rowPluralName("public component trees to clean permissions of");
    massUpdate.update("delete from group_roles where resource_id = ? and role in ('user', 'codeviewer')");
    massUpdate.update("delete from user_roles where resource_id = ? and role in ('user', 'codeviewer')");
    massUpdate.execute(MakeComponentsPrivateBaseOnPermissions::handleCleanPermissionsOfPublicComponents);
  }

  private static boolean handleCleanPermissionsOfPublicComponents(Select.Row row, SqlStatement update, int updateIndex) throws SQLException {
    long id = row.getLong(1);
    switch (updateIndex) {
      case 0:
      case 1:
        update.setLong(1, id);
        return true;
      default:
        throw new IllegalArgumentException("Unsupported update index " + updateIndex);
    }
  }
}
