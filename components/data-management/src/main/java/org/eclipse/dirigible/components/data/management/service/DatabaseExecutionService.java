/*
 * Copyright (c) 2023 SAP SE or an SAP affiliate company and Eclipse Dirigible contributors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-FileCopyrightText: 2023 SAP SE or an SAP affiliate company and Eclipse Dirigible contributors
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.dirigible.components.data.management.service;


import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.dirigible.commons.api.helpers.GsonHelper;
import org.eclipse.dirigible.components.data.management.helpers.DatabaseErrorHelper;
import org.eclipse.dirigible.components.data.management.helpers.DatabaseQueryHelper;
import org.eclipse.dirigible.components.data.management.helpers.DatabaseQueryHelper.RequestExecutionCallback;
import org.eclipse.dirigible.components.data.management.helpers.DatabaseResultSetHelper;
import org.eclipse.dirigible.components.data.sources.domain.DataSource;
import org.eclipse.dirigible.components.data.sources.manager.DataSourcesManager;
import org.eclipse.dirigible.components.data.sources.service.DataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The Class DataSourceMetadataService.
 */
@Service
public class DatabaseExecutionService {
	
	/** The Constant logger. */
	private static final Logger logger = LoggerFactory.getLogger(DatabaseExecutionService.class);
	
	/** The Constant CREATE_PROCEDURE. */
	private static final String CREATE_PROCEDURE = "CREATE PROCEDURE";

	/** The Constant SCRIPT_DELIMITER. */
	private static final String SCRIPT_DELIMITER = ";";
	
	/** The Constant PROCEDURE_DELIMITER. */
	private static final String PROCEDURE_DELIMITER = "--";

	/** The limited. */
	private final boolean LIMITED = true;

	/** The data sources manager. */
	private final DataSourcesManager datasourceManager;

	/** The data sources service. */
	private final DataSourceService datasourceService;

	/**
	 * Instantiates a new data source endpoint.
	 *
	 * @param datasourceManager the datasource manager
	 * @param datasourceService the datasource service
	 */
	@Autowired
	public DatabaseExecutionService(DataSourcesManager datasourceManager,DataSourceService datasourceService) {
		this.datasourceManager = datasourceManager;
		this.datasourceService = datasourceService;
	}

	/**
	 * Gets the data sources.
	 *
	 * @return the data sources
	 */
	public Set<String> getDataSourcesNames() {
		return datasourceService.getAll().stream().map(DataSource::getName).collect(Collectors.toSet());
	}


	/**
	 * Execute query.
	 *
	 * @param datasource
	 *            the datasource
	 * @param sql
	 *            the sql
	 * @param isJson
	 *            the is json
	 * @param isCsv
	 *            the is csv
	 * @return the string
	 */
	public String executeQuery(String datasource, String sql, boolean isJson, boolean isCsv) {
		javax.sql.DataSource dataSource = datasourceManager.getDataSource(datasource);
		if (dataSource != null) {
			return executeStatement(dataSource, sql, true, isJson, isCsv, true);
		}
		return null;
	}

	/**
	 * Execute update.
	 *
	 * @param datasource
	 *            the datasource
	 * @param sql
	 *            the sql
	 * @param isJson
	 *            the is json
	 * @param isCsv
	 *            the is csv
	 * @return the string
	 */
	public String executeUpdate(String datasource, String sql, boolean isJson, boolean isCsv) {
		javax.sql.DataSource dataSource = datasourceManager.getDataSource(datasource);
		if (dataSource != null) {
			return executeStatement(dataSource, sql, false, isJson, isCsv, true);
		}
		return null;
	}

	/**
	 * Execute update.
	 *
	 * @param datasource
	 *            the datasource
	 * @param sql
	 *            the sql
	 * @param isJson
	 *            the is json
	 * @param isCsv
	 *            the is csv
	 * @return the string
	 */
	public String executeProcedure(String datasource, String sql, boolean isJson, boolean isCsv) {
		javax.sql.DataSource dataSource = datasourceManager.getDataSource(datasource);
		if (dataSource != null) {
			return executeProcedure(dataSource, sql, isJson, isCsv);
		}
		return null;
	}

	/**
	 * Execute.
	 *
	 * @param datasource
	 *            the datasource
	 * @param sql
	 *            the sql
	 * @param isJson
	 *            the is json
	 * @param isCsv
	 *            the is csv
	 * @return the string
	 */
	public String execute(String datasource, String sql, boolean isJson, boolean isCsv) {
		javax.sql.DataSource dataSource = datasourceManager.getDataSource(datasource);
		if (dataSource != null) {
			return executeStatement(dataSource, sql, true, isJson, isCsv, true);
		}
		return null;
	}

	/**
	 * Execute statement.
	 *
	 * @param dataSource
	 *            the data source
	 * @param sql
	 *            the sql
	 * @param isQuery
	 *            the is query
	 * @param isJson
	 *            the is json
	 * @param isCsv
	 *            the is csv
	 * @return the string
	 */
	public String executeStatement(javax.sql.DataSource dataSource, String sql, boolean isQuery, boolean isJson, boolean isCsv, boolean limited) {

		if ((sql == null) || (sql.length() == 0)) {
			return "";
		}

		List<String> results = new ArrayList<String>();
		List<String> errors = new ArrayList<String>();

		StringTokenizer tokenizer = new StringTokenizer(sql, getDelimiter(sql));
		while (tokenizer.hasMoreTokens()) {
			String line = tokenizer.nextToken();
			if ("".equals(line.trim())) {
				continue;
			}

			try (Connection connection = dataSource.getConnection()) {
				DatabaseQueryHelper.executeSingleStatement(connection, line, isQuery, new RequestExecutionCallback() {
					@Override
					public void updateDone(int recordsCount) {
						results.add(recordsCount + "");
					}

					@Override
					public void queryDone(ResultSet rs) {
						try {
							if (isJson) {
								results.add(DatabaseResultSetHelper.toJson(rs, limited, true));
							} else if (isCsv) {
								results.add(DatabaseResultSetHelper.toCsv(rs, limited, false));
							} else {
								results.add(DatabaseResultSetHelper.print(rs, limited));
							}
						} catch (SQLException e) {
							if (logger.isWarnEnabled()) {logger.warn(e.getMessage(), e);}
							errors.add(e.getMessage());
						}
					}

					@Override
					public void error(Throwable t) {
						if (logger.isWarnEnabled()) {logger.warn(t.getMessage(), t);}
						errors.add(t.getMessage());
					}
				});
			} catch (SQLException e) {
				if (logger.isWarnEnabled()) {logger.warn(e.getMessage(), e);}
				errors.add(e.getMessage());
			}
		}

		if (!errors.isEmpty()) {
			if (isJson) {
				return DatabaseErrorHelper.toJson(String.join("\n", errors));
			}
			return DatabaseErrorHelper.print(String.join("\n", errors));
		}

		return String.join("\n", results);
	}

	/**
	 * Execute procedure.
	 *
	 * @param dataSource
	 *            the data source
	 * @param sql
	 *            the sql
	 * @param isJson
	 *            the is json
	 * @param isCsv
	 *            the is csv
	 * @return the string
	 */
	private String executeProcedure(javax.sql.DataSource dataSource, String sql, boolean isJson, boolean isCsv) {

		if ((sql == null) || (sql.length() == 0)) {
			return "";
		}

		List<String> results = new ArrayList<String>();
		List<String> errors = new ArrayList<String>();

		StringTokenizer tokenizer = new StringTokenizer(sql, getDelimiter(sql));
		while (tokenizer.hasMoreTokens()) {
			String line = tokenizer.nextToken();
			if ("".equals(line.trim())) {
				continue;
			}

			try (Connection connection = dataSource.getConnection()) {
				DatabaseQueryHelper.executeSingleProcedure(connection, line, new RequestExecutionCallback() {

					@Override
					public void updateDone(int recordsCount) {
						results.add(recordsCount + "");
					}

					@Override
					public void queryDone(ResultSet rs) {
						try {
							if (isJson) {
								results.add(DatabaseResultSetHelper.toJson(rs, LIMITED, true));
							} else if (isCsv) {
								results.add(DatabaseResultSetHelper.toCsv(rs, LIMITED, false));
							} else {
								results.add(DatabaseResultSetHelper.print(rs, LIMITED));
							}
						} catch (SQLException e) {
							if (logger.isWarnEnabled()) {logger.warn(e.getMessage(), e);}
							errors.add(e.getMessage());
						}
					}

					@Override
					public void error(Throwable t) {
						if (logger.isWarnEnabled()) {logger.warn(t.getMessage(), t);}
						errors.add(t.getMessage());
					}
				});
			} catch (SQLException e) {
				if (logger.isWarnEnabled()) {logger.warn(e.getMessage(), e);}
				errors.add(e.getMessage());
			}
		}

		if (!errors.isEmpty()) {
			if (isJson) {
				return DatabaseErrorHelper.toJson(String.join("\n", errors));
			}
			return DatabaseErrorHelper.print(String.join("\n", errors));
		}

		if (isJson) {
			return GsonHelper.toJson(results);
		}
		return String.join("\n", results);
	}
	
	/**
	 * Gets the delimiter.
	 *
	 * @param sql the sql
	 * @return the delimiter
	 */
	private String getDelimiter(String sql) {
		if (StringUtils.containsIgnoreCase(sql, CREATE_PROCEDURE)) {
			return PROCEDURE_DELIMITER;
		}
		return SCRIPT_DELIMITER;
	}
}