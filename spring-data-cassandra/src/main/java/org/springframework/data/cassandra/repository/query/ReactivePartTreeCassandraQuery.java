/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.cassandra.repository.query;

import org.springframework.data.cassandra.convert.UpdateMapper;
import org.springframework.data.cassandra.core.ReactiveCassandraOperations;
import org.springframework.data.cassandra.core.StatementFactory;
import org.springframework.data.cassandra.core.query.Query;
import org.springframework.data.cassandra.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.mapping.CassandraPersistentEntity;
import org.springframework.data.repository.query.QueryCreationException;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.parser.PartTree;

import com.datastax.driver.core.Statement;

/**
 * Reactive PartTree {@link RepositoryQuery} implementation for Cassandra.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class ReactivePartTreeCassandraQuery extends AbstractReactiveCassandraQuery {

	private final CassandraMappingContext mappingContext;

	private final PartTree tree;

	private final StatementFactory statementFactory;

	/**
	 * Create a new {@link ReactivePartTreeCassandraQuery} from the given {@link ReactiveCassandraQueryMethod} and
	 * {@link ReactiveCassandraOperations}.
	 *
	 * @param queryMethod must not be {@literal null}.
	 * @param operations must not be {@literal null}.
	 */
	public ReactivePartTreeCassandraQuery(ReactiveCassandraQueryMethod queryMethod,
			ReactiveCassandraOperations operations) {

		super(queryMethod, operations);

		this.tree = new PartTree(queryMethod.getName(), queryMethod.getEntityInformation().getJavaType());
		this.mappingContext = operations.getConverter().getMappingContext();
		this.statementFactory = new StatementFactory(new UpdateMapper(operations.getConverter()));
	}

	/**
	 * Return the {@link PartTree} backing the query.
	 *
	 * @return the tree
	 */
	public PartTree getTree() {
		return tree;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.cassandra.repository.query.AbstractCassandraQuery#createQuery(org.springframework.data.cassandra.repository.query.CassandraParameterAccessor, boolean)
	 */
	@Override
	protected Statement createQuery(CassandraParameterAccessor parameterAccessor) {

		CassandraQueryCreator queryCreator = new CassandraQueryCreator(tree, parameterAccessor, mappingContext);
		Query query = queryCreator.createQuery();

		try {
			if (tree.isLimiting()) {
				query.limit(tree.getMaxResults());
			}

			CassandraPersistentEntity<?> persistentEntity = mappingContext
					.getRequiredPersistentEntity(getQueryMethod().getDomainClass());

			return statementFactory.select(query, persistentEntity);
		} catch (RuntimeException e) {
			throw QueryCreationException.create(getQueryMethod(), e);
		}
	}
}
