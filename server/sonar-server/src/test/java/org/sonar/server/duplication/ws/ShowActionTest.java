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
package org.sonar.server.duplication.ws;

import java.util.function.Function;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.metric.MetricDto;
import org.sonar.server.component.TestComponentFinder;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.startup.RegisterMetrics;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.TestResponse;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.component.SnapshotTesting.newAnalysis;
import static org.sonar.db.measure.MeasureTesting.newMeasureDto;
import static org.sonar.test.JsonAssert.assertJson;

public class ShowActionTest {

  private static MetricDto dataMetric = RegisterMetrics.MetricToDto.INSTANCE.apply(CoreMetrics.DUPLICATIONS_DATA);

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public UserSessionRule userSessionRule = UserSessionRule.standalone();

  @Rule
  public DbTester db = DbTester.create();
  private DuplicationsParser parser = new DuplicationsParser(db.getDbClient().componentDao());
  private ShowResponseBuilder showResponseBuilder = new ShowResponseBuilder(db.getDbClient());

  private WsActionTester ws = new WsActionTester(new ShowAction(db.getDbClient(), parser, showResponseBuilder, userSessionRule, TestComponentFinder.from(db)));

  @Before
  public void setUp() {
    db.getDbClient().metricDao().insert(db.getSession(), dataMetric);
    db.commit();
  }

  @Test
  public void define_ws() {
    WebService.Action show = ws.getDef();
    assertThat(show).isNotNull();
    assertThat(show.handler()).isNotNull();
    assertThat(show.since()).isEqualTo("4.4");
    assertThat(show.isInternal()).isFalse();
    assertThat(show.responseExampleAsString()).isNotEmpty();
    assertThat(show.params()).hasSize(2);
  }

  @Test
  public void get_duplications_by_file_key() throws Exception {
    TestRequest request = newBaseRequest();
    verifyCallToFileWithDuplications(file -> request.setParam("key", file.getDbKey()));
  }

  @Test
  public void get_duplications_by_file_id() throws Exception {
    TestRequest request = newBaseRequest();
    verifyCallToFileWithDuplications(file -> request.setParam("uuid", file.uuid()));
  }

  @Test
  public void return_file_with_missing_duplication_data() throws Exception {
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto file = db.components().insertComponent(newFileDto(project).setDbKey("foo.js"));
    db.components().insertSnapshot(newAnalysis(project));

    userSessionRule.addProjectPermission(UserRole.CODEVIEWER, project);

    TestResponse result = newBaseRequest().setParam("key", file.getDbKey()).execute();

    assertJson(result.getInput()).isSimilarTo("{\n" +
      "  \"duplications\": [],\n" +
      "  \"files\": {}\n" +
      "}");
  }

  @Test
  public void fail_if_file_does_not_exist() throws Exception {
    expectedException.expect(NotFoundException.class);

    newBaseRequest().setParam("key", "missing").execute();
  }

  @Test
  public void fail_if_user_is_not_allowed_to_access_project() throws Exception {
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto file = db.components().insertComponent(newFileDto(project));

    expectedException.expect(ForbiddenException.class);

    newBaseRequest().setParam("key", file.getDbKey()).execute();
  }

  @Test
  public void fail_if_no_parameter_provided() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Either 'uuid' or 'key' must be provided, not both");

    newBaseRequest().execute();
  }

  private TestRequest newBaseRequest() {
    return ws.newRequest();
  }

  private void verifyCallToFileWithDuplications(Function<ComponentDto, TestRequest> requestFactory) throws Exception {
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto file = db.components().insertComponent(newFileDto(project).setDbKey("foo.js"));
    SnapshotDto snapshot = db.components().insertSnapshot(newAnalysis(project));
    String xml = "<duplications>\n" +
      "  <g>\n" +
      "    <b s=\"31\" l=\"5\" r=\"foo.js\"/>\n" +
      "    <b s=\"20\" l=\"5\" r=\"foo.js\"/>\n" +
      "  </g>\n" +
      "</duplications>\n";
    db.getDbClient().measureDao().insert(db.getSession(), newMeasureDto(dataMetric, file, snapshot).setData(xml));
    db.commit();

    userSessionRule.addProjectPermission(UserRole.CODEVIEWER, project);

    TestRequest request = requestFactory.apply(file);
    TestResponse result = request.execute();

    assertJson(result.getInput()).isSimilarTo("{\"duplications\":[" +
      "{\"blocks\":[{\"from\":20,\"size\":5,\"_ref\":\"1\"},{\"from\":31,\"size\":5,\"_ref\":\"1\"}]}]," +
      "\"files\":{\"1\":{\"key\":\"foo.js\",\"uuid\":\"" + file.uuid() + "\"}}}");
  }
}
