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
// @flow
import React from 'react';
import SourceViewer from '../../../components/SourceViewer/SourceViewer';
import { scrollToElement } from '../../../helpers/scrolling';
import type { Issue } from '../../../components/issue/types';

type Props = {|
  loadIssues: () => Promise<*>,
  onIssueChange: Issue => void,
  onIssueSelect: string => void,
  onLocationSelect: number => void,
  openIssue: Issue,
  selectedFlowIndex: ?number,
  selectedLocationIndex: ?number
|};

export default class IssuesSourceViewer extends React.PureComponent {
  node: HTMLElement;
  props: Props;

  componentDidUpdate(prevProps: Props) {
    if (
      prevProps.openIssue !== this.props.openIssue &&
      prevProps.openIssue.component === this.props.openIssue.component
    ) {
      this.scrollToIssue();
    }
  }

  scrollToIssue = () => {
    const element = this.node.querySelector(`[data-issue="${this.props.openIssue.key}"]`);
    if (element) {
      this.handleScroll(element);
    }
  };

  handleScroll = (element: HTMLElement) => {
    const offset = window.innerHeight / 2;
    scrollToElement(element, offset - 100, offset);
  };

  render() {
    const { openIssue, selectedFlowIndex, selectedLocationIndex } = this.props;

    const locations = selectedFlowIndex != null
      ? openIssue.flows[selectedFlowIndex]
      : openIssue.flows.length > 0 ? openIssue.flows[0] : openIssue.secondaryLocations;

    const locationMessage = locations != null &&
      selectedLocationIndex != null &&
      locations.length >= selectedLocationIndex
      ? { index: selectedLocationIndex, text: locations[selectedLocationIndex].msg }
      : undefined;

    return (
      <div ref={node => (this.node = node)}>
        <SourceViewer
          aroundLine={openIssue.line}
          component={openIssue.component}
          displayAllIssues={true}
          highlightedLocations={locations}
          highlightedLocationMessage={locationMessage}
          loadIssues={this.props.loadIssues}
          onLoaded={this.scrollToIssue}
          onLocationSelect={this.props.onLocationSelect}
          onIssueChange={this.props.onIssueChange}
          onIssueSelect={this.props.onIssueSelect}
          scroll={this.handleScroll}
          selectedIssue={openIssue.key}
        />
      </div>
    );
  }
}
