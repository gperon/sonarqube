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
package org.sonar.server.platform.db.migration.sql;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.dialect.Dialect;
import org.sonar.db.dialect.H2;
import org.sonar.db.dialect.MariaDb;
import org.sonar.db.dialect.MsSql;
import org.sonar.db.dialect.MySql;
import org.sonar.db.dialect.Oracle;
import org.sonar.db.dialect.PostgreSql;

/**
 * Generate a SQL query to drop multiple columns from a table
 */
public class DropColumnsBuilder {

    private static final String ALTER_TABLE = "ALTER TABLE ";

    private final Dialect dialect;
    private final String tableName;
    private final String[] columns;

    public DropColumnsBuilder(Dialect dialect, String tableName, String... columns) {
        this.tableName = tableName;
        this.dialect = dialect;
        this.columns = columns;
    }

  private String getOracleStatement() {
    StringBuilder sql = new StringBuilder().append(ALTER_TABLE).append(tableName).append(" ");
    sql.append("SET UNUSED (");
    dropColumns(sql, "", columns);
    sql.append(")");
    return sql.toString();
  }

  private String getMsSQLStatement(String... columnNames) {
    StringBuilder sql = new StringBuilder().append(ALTER_TABLE).append(tableName).append(" ");
    sql.append("DROP COLUMN ");
    dropColumns(sql, "", columnNames);
    return sql.toString();
  }

  private static void dropColumns(StringBuilder sql, String columnPrefix, String... columnNames) {
    Iterator<String> columnNamesIterator = Arrays.stream(columnNames).iterator();
    while (columnNamesIterator.hasNext()) {
      sql.append(columnPrefix);
      sql.append(columnNamesIterator.next());
      if (columnNamesIterator.hasNext()) {
        sql.append(", ");
      }
    }

    private String getOracleStatement() {
        StringBuilder sql2 = new StringBuilder().append(ALTER_TABLE).append(tableName).append(" ");
        sql2.append("DROP (");
        dropColumns(sql2, "", columns);
        sql2.append(")");
        return sql2.toString();
    }

    private String getMsSQLStatement(String... columnNames) {
        StringBuilder sql1 = new StringBuilder().append(ALTER_TABLE).append(tableName).append(" ");
        sql1.append("DROP COLUMN ");
        dropColumns(sql1, "", columnNames);
        return sql1.toString();
    }

    private static void dropColumns(StringBuilder sql, String columnPrefix, String... columnNames) {
        Iterator<String> columnNamesIterator = Arrays.stream(columnNames).iterator();
        while (columnNamesIterator.hasNext()) {
            sql.append(columnPrefix);
            sql.append(columnNamesIterator.next());
            if (columnNamesIterator.hasNext()) {
                sql.append(", ");
            }
        }
    }

}
