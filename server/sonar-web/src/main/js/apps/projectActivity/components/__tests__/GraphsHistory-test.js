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
import React from 'react';
import { shallow } from 'enzyme';
import GraphsHistory from '../GraphsHistory';
import { DEFAULT_GRAPH } from '../../utils';

const ANALYSES = [
  {
    key: 'A1',
    date: new Date('2016-10-27T16:33:50+0200'),
    events: [
      {
        key: 'E1',
        category: 'VERSION',
        name: '6.5-SNAPSHOT'
      }
    ]
  },
  {
    key: 'A2',
    date: new Date('2016-10-27T12:21:15+0200'),
    events: []
  },
  {
    key: 'A3',
    date: new Date('2016-10-26T12:17:29+0200'),
    events: [
      {
        key: 'E2',
        category: 'OTHER',
        name: 'foo'
      },
      {
        key: 'E3',
        category: 'VERSION',
        name: '6.4'
      }
    ]
  }
];

const SERIES = [
  {
    name: 'bugs',
    translatedName: 'metric.bugs.name',
    data: [
      { x: new Date('2016-10-27T16:33:50+0200'), y: 5 },
      { x: new Date('2016-10-27T12:21:15+0200'), y: 16 },
      { x: new Date('2016-10-26T12:17:29+0200'), y: 12 }
    ]
  }
];

const DEFAULT_PROPS = {
  analyses: ANALYSES,
  eventFilter: '',
  graph: DEFAULT_GRAPH,
  graphs: [SERIES],
  graphEndDate: null,
  graphStartDate: null,
  leakPeriodDate: '2017-05-16T13:50:02+0200',
  loading: false,
  measuresHistory: [],
  removeCustomMetric: () => {},
  selectedDate: null,
  series: SERIES,
  updateGraphZoom: () => {},
  updateSelectedDate: () => {}
};

it('should correctly render a graph', () => {
  expect(shallow(<GraphsHistory {...DEFAULT_PROPS} />)).toMatchSnapshot();
});

it('should correctly render multiple graphs', () => {
  expect(shallow(<GraphsHistory {...DEFAULT_PROPS} graphs={[SERIES, SERIES]} />)).toMatchSnapshot();
});

it('should correctly filter events', () => {
  expect(shallow(<GraphsHistory {...DEFAULT_PROPS} />).instance().getEvents()).toMatchSnapshot();
  expect(
    shallow(<GraphsHistory {...DEFAULT_PROPS} eventFilter="OTHER" />).instance().getEvents()
  ).toMatchSnapshot();
});

it('should show a loading view instead of the graph', () => {
  expect(
    shallow(<GraphsHistory {...DEFAULT_PROPS} loading={true} />).find('DeferredSpinner')
  ).toHaveLength(1);
});

it('should show that there is no history data', () => {
  expect(shallow(<GraphsHistory {...DEFAULT_PROPS} series={[]} />)).toMatchSnapshot();
  expect(
    shallow(
      <GraphsHistory
        {...DEFAULT_PROPS}
        series={[
          {
            name: 'bugs',
            translatedName: 'metric.bugs.name',
            data: [{ x: new Date('2016-10-27T16:33:50+0200'), y: undefined }]
          }
        ]}
      />
    )
  ).toMatchSnapshot();
});
