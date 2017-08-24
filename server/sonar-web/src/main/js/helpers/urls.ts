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
import { stringify } from 'querystring';
import { getProfilePath } from '../apps/quality-profiles/utils';

interface Query {
  [x: string]: string;
}

interface Location {
  pathname: string;
  query?: Query;
}

/**
 * Generate URL for a component's home page
 */
export function getComponentUrl(componentKey: string): string {
  return (window as any).baseUrl + '/dashboard?id=' + encodeURIComponent(componentKey);
}

export function getProjectUrl(key: string): Location {
  return { pathname: '/dashboard', query: { id: key } };
}

/**
 * Generate URL for a global issues page
 */
export function getIssuesUrl(query: Query): Location {
  return { pathname: '/issues', query };
}

/**
 * Generate URL for a component's issues page
 */
export function getComponentIssuesUrl(componentKey: string, query?: Query): Location {
  return { pathname: '/project/issues', query: { ...query || {}, id: componentKey } };
}

export function getComponentIssuesUrlAsString(componentKey: string, query?: Query): string {
  const path = getComponentIssuesUrl(componentKey, query);
  return `${(window as any).baseUrl}${path.pathname}?${stringify(path.query)}`;
}

/**
 * Generate URL for a component's drilldown page
 */
export function getComponentDrilldownUrl(componentKey: string, metric: string): Location {
  return { pathname: '/component_measures', query: { id: componentKey, metric } };
}

/**
 * Generate URL for a component's measure history
 */
export function getComponentMeasureHistory(componentKey: string, metric: string): Location {
  return {
    pathname: '/project/activity',
    query: { id: componentKey, graph: 'custom', custom_metrics: metric }
  };
}

/**
 * Generate URL for a component's permissions page
 */
export function getComponentPermissionsUrl(componentKey: string): Location {
  return { pathname: '/project_roles', query: { id: componentKey } };
}

/**
 * Generate URL for a quality profile
 */
export function getQualityProfileUrl(
  name: string,
  language: string,
  organization?: string | null
): Location {
  return getProfilePath(name, language, organization);
}

export function getQualityGateUrl(key: string, organization?: string | null): Location {
  return {
    pathname: getQualityGatesUrl(organization).pathname + '/show/' + encodeURIComponent(key)
  };
}

export function getQualityGatesUrl(organization?: string | null): Location {
  return {
    pathname:
      (organization ? '/organizations/' + encodeURIComponent(organization) : '') + '/quality_gates'
  };
}

/**
 * Generate URL for the rules page
 */
export function getRulesUrl(query: { [x: string]: string }, organization?: string | null): string {
  const path = organization ? `/organizations/${organization}/rules` : '/coding_rules';

  if (query) {
    const serializedQuery = Object.keys(query)
      .map(criterion => `${encodeURIComponent(criterion)}=${encodeURIComponent(query[criterion])}`)
      .join('|');

    // return a string (not { pathname }) to help react-router's Link handle this properly
    return path + '#' + serializedQuery;
  }

  return path;
}

/**
 * Generate URL for the rules page filtering only active deprecated rules
 */
export function getDeprecatedActiveRulesUrl(query = {}, organization?: string | null): string {
  const baseQuery = { activation: 'true', statuses: 'DEPRECATED' };
  return getRulesUrl({ ...query, ...baseQuery }, organization);
}

export function getProjectsUrl(): string {
  return (window as any).baseUrl + '/projects';
}

export function getMarkdownHelpUrl(): string {
  return (window as any).baseUrl + '/markdown/help';
}
