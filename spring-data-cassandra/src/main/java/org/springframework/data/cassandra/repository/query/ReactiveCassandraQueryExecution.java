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

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.cassandra.core.ReactiveCassandraOperations;
import org.springframework.data.cassandra.mapping.CassandraMappingContext;
import org.springframework.data.convert.EntityInstantiators;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.util.ClassUtils;

import com.datastax.driver.core.Statement;

/**
 * Reactive query executions for Cassandra.
 *
 * @author Mark Paluch
 * @since 2.0
 */
interface ReactiveCassandraQueryExecution {

	Object execute(Statement statement, Class<?> type);

	/**
	 * {@link ReactiveCassandraQueryExecution} for collection returning queries.
	 *
	 * @author Mark Paluch
	 */
	@RequiredArgsConstructor
	final class CollectionExecution implements ReactiveCassandraQueryExecution {

		private final @NonNull ReactiveCassandraOperations operations;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.cassandra.repository.query.ReactiveCassandraQueryExecution#execute(java.lang.String, java.lang.Class)
		 */
		@Override
		public Object execute(Statement statement, Class<?> type) {
			return operations.select(statement, type);
		}
	}

	/**
	 * {@link ReactiveCassandraQueryExecution} to return a single entity.
	 *
	 * @author Mark Paluch
	 */
	@RequiredArgsConstructor
	final class SingleEntityExecution implements ReactiveCassandraQueryExecution {

		private final @NonNull ReactiveCassandraOperations operations;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.cassandra.repository.query.ReactiveCassandraQueryExecution#execute(java.lang.String, java.lang.Class)
		 */
		@Override
		public Object execute(Statement statement, Class<?> type) {
			return operations.selectOne(statement, type);
		}
	}

	/**
	 * An {@link ReactiveCassandraQueryExecution} that wraps the results of the given delegate with the given result
	 * processing.
	 *
	 * @author Mark Paluch
	 */
	@RequiredArgsConstructor
	final class ResultProcessingExecution implements ReactiveCassandraQueryExecution {

		private final @NonNull ReactiveCassandraQueryExecution delegate;
		private final @NonNull Converter<Object, Object> converter;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.cassandra.repository.query.ReactiveCassandraQueryExecution#execute(java.lang.String, java.lang.Class)
		 */
		@Override
		public Object execute(Statement statement, Class<?> type) {
			return converter.convert(delegate.execute(statement, type));
		}
	}

	/**
	 * A {@link Converter} to post-process all source objects using the given {@link ResultProcessor}.
	 *
	 * @author Mark Paluch
	 */
	@RequiredArgsConstructor
	final class ResultProcessingConverter implements Converter<Object, Object> {

		private final @NonNull ResultProcessor processor;
		private final @NonNull CassandraMappingContext mappingContext;
		private final @NonNull EntityInstantiators instantiators;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		public Object convert(Object source) {

			ReturnedType returnedType = processor.getReturnedType();

			if (ClassUtils.isPrimitiveOrWrapper(returnedType.getReturnedType())) {
				return source;
			}

			if (source != null && returnedType.isInstance(source)) {
				return source;
			}

			Converter<Object, Object> converter = new DtoInstantiatingConverter(returnedType.getReturnedType(),
					mappingContext, instantiators);

			return processor.processResult(source, converter);
		}
	}
}
