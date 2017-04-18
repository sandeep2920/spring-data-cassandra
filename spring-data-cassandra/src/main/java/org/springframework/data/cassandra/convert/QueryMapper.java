/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.data.cassandra.convert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cassandra.core.cql.CqlIdentifier;
import org.springframework.data.cassandra.core.query.ColumnName;
import org.springframework.data.cassandra.core.query.Columns;
import org.springframework.data.cassandra.core.query.Columns.ColumnSelector;
import org.springframework.data.cassandra.core.query.Columns.FunctionCall;
import org.springframework.data.cassandra.core.query.Columns.Selector;
import org.springframework.data.cassandra.core.query.Criteria;
import org.springframework.data.cassandra.core.query.CriteriaDefinition;
import org.springframework.data.cassandra.core.query.CriteriaDefinition.Predicate;
import org.springframework.data.cassandra.core.query.Filter;
import org.springframework.data.cassandra.mapping.CassandraPersistentEntity;
import org.springframework.data.cassandra.mapping.CassandraPersistentProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.PropertyHandler;
import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.context.PersistentPropertyPath;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.TypeInformation;
import org.springframework.util.Assert;

/**
 * Map {@link org.springframework.data.cassandra.core.query.Query} to CQL-specific data types.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class QueryMapper {

	private final CassandraConverter converter;

	private final MappingContext<? extends CassandraPersistentEntity<?>, CassandraPersistentProperty> mappingContext;

	/**
	 * Creates a new {@link QueryMapper} with the given {@link CassandraConverter}.
	 *
	 * @param converter must not be {@literal null}.
	 */
	public QueryMapper(CassandraConverter converter) {

		Assert.notNull(converter, "CassandraConverter must not be null");

		this.converter = converter;
		this.mappingContext = converter.getMappingContext();
	}

	/**
	 * Map a {@link Filter} with a {@link CassandraPersistentEntity type hint}. Filter mapping translates property names
	 * to column names and maps {@link Predicate} values to simple Cassandra values.
	 *
	 * @param filter must not be {@literal null}.
	 * @param entity must not be {@literal null}.
	 * @return the mapped {@link Filter}.
	 */
	public Filter getMappedObject(Filter filter, CassandraPersistentEntity<?> entity) {

		Assert.notNull(filter, "Filter must not be null");
		Assert.notNull(entity, "CassandraPersistentEntity must not be null");

		List<CriteriaDefinition> result = new ArrayList<>();

		for (CriteriaDefinition criteriaDefinition : filter) {

			Field field = createPropertyField(entity, criteriaDefinition.getColumnName());

			Predicate predicate = criteriaDefinition.getPredicate();

			Optional<Object> value = Optional.ofNullable(predicate.getValue());
			TypeInformation<?> typeInformation = getTypeInformation(field, value);
			Optional<Object> mappedValue = converter.convertToCassandraColumn(value, typeInformation);

			Predicate mappedPredicate = new Predicate(predicate.getOperator(), mappedValue.orElse(null));
			result.add(Criteria.of(field.getMappedKey(), mappedPredicate));
		}

		return Filter.from(result);
	}

	/**
	 * Return {@link ColumnSelector}s for all columns of {@link CassandraPersistentEntity}.
	 *
	 * @param entity must not be {@literal null}.
	 * @return {@link ColumnSelector}s for all columns of {@link CassandraPersistentEntity}.
	 */
	public List<Selector> getColumns(CassandraPersistentEntity<?> entity) {

		return entity.getPersistentProperties() //
				.flatMap(p -> p.getColumnNames().stream()).map(ColumnSelector::from) //
				.collect(Collectors.toList());
	}

	/**
	 * Map {@link Columns} with a {@link CassandraPersistentEntity type hint} to {@link ColumnSelector}s.
	 *
	 * @param columns must not be {@literal null}.
	 * @param entity must not be {@literal null}.
	 * @return the mapped {@link Selector}s.
	 */
	public List<Selector> getMappedSelectors(Columns columns, CassandraPersistentEntity<?> entity) {

		Assert.notNull(columns, "Columns must not be null");
		Assert.notNull(entity, "CassandraPersistentEntity must not be null");

		if (columns.isEmpty()) {
			return Collections.emptyList();
		}

		List<Selector> selectors = new ArrayList<>();

		for (ColumnName column : columns) {

			Field field = createPropertyField(entity, column);

			columns.getSelector(column).ifPresent(selector -> {
				getCqlIdentifier(column, field).ifPresent(cqlIdentifier -> {
					selectors.add(getMappedSelector(selector, cqlIdentifier));
				});
			});
		}

		if (columns.isEmpty()) {

			entity.doWithProperties((PropertyHandler<CassandraPersistentProperty>) property -> {

				if (property.isCompositePrimaryKey()) {
					for (CqlIdentifier cqlIdentifier : property.getColumnNames()) {
						selectors.add(ColumnSelector.from(cqlIdentifier.toCql()));
					}
				} else {
					selectors.add(ColumnSelector.from(property.getColumnName().toCql()));
				}
			});
		}

		return selectors;
	}

	private Selector getMappedSelector(Selector selector, CqlIdentifier cqlIdentifier) {

		if (selector instanceof ColumnSelector) {

			ColumnSelector columnSelector = (ColumnSelector) selector;

			ColumnSelector mappedColumnSelector = ColumnSelector.from(cqlIdentifier);

			return columnSelector.getAlias() //
					.map(mappedColumnSelector::as) //
					.orElse(mappedColumnSelector);
		}

		if (selector instanceof FunctionCall) {

			FunctionCall functionCall = (FunctionCall) selector;

			List<Object> mappedParameters = functionCall.getParameters() //
					.stream() //
					.map(o -> {

						if (o instanceof Selector) {
							return getMappedSelector((Selector) o, cqlIdentifier);
						}

						return o;
					}) //
					.collect(Collectors.toList());

			FunctionCall mappedCall = FunctionCall.from(functionCall.getExpression(), mappedParameters.toArray());

			return functionCall.getAlias() //
					.map(mappedCall::as) //
					.orElse(mappedCall);
		}

		throw new IllegalArgumentException(String.format("Selector [%s] not supported", selector));
	}

	/**
	 * Map {@link Columns} with a {@link CassandraPersistentEntity type hint} to column names for included columns.
	 * Function call selectors or other {@link org.springframework.data.cassandra.core.query.Columns.Selector} types are
	 * not included.
	 *
	 * @param columns must not be {@literal null}.
	 * @param entity must not be {@literal null}.
	 * @return the mapped column names.
	 */
	public List<String> getMappedColumnNames(Columns columns, CassandraPersistentEntity<?> entity) {

		Assert.notNull(columns, "Columns must not be null");
		Assert.notNull(entity, "CassandraPersistentEntity must not be null");

		if (columns.isEmpty()) {
			return Collections.emptyList();
		}

		List<String> columnNames = new ArrayList<>();

		Set<PersistentProperty<?>> seen = new HashSet<>();

		for (ColumnName column : columns) {

			Field field = createPropertyField(entity, column);

			field.getProperty().ifPresent(seen::add);

			columns.getSelector(column) //
					.filter(selector -> selector instanceof ColumnSelector) //
					.ifPresent(columnExpression -> {

						getCqlIdentifier(column, field) //
								.map(CqlIdentifier::toCql) //
								.ifPresent(columnNames::add);
					});
		}

		if (columns.isEmpty()) {

			entity.doWithProperties((PropertyHandler<CassandraPersistentProperty>) property -> {

				if (property.isCompositePrimaryKey()) {
					return;
				}

				if (seen.add(property)) {
					columnNames.add(property.getColumnName().toCql());
				}
			});
		}

		return columnNames;
	}

	public Sort getMappedSort(Sort sort, CassandraPersistentEntity<?> entity) {

		Assert.notNull(sort, "Sort must not be null");
		Assert.notNull(entity, "CassandraPersistentEntity must not be null");

		if (!sort.iterator().hasNext()) {
			return sort;
		}

		List<Order> mappedOrders = new ArrayList<>();

		for (Order order : sort) {

			ColumnName columnName = ColumnName.from(order.getProperty());
			Field field = createPropertyField(entity, columnName);

			Order mappedOrder = getCqlIdentifier(columnName, field)
					.map(cqlIdentifier -> new Order(order.getDirection(), cqlIdentifier.toCql())).orElse(order);
			mappedOrders.add(mappedOrder);
		}

		return new Sort(mappedOrders);
	}

	private Optional<CqlIdentifier> getCqlIdentifier(ColumnName column, Field field) {

		try {

			if (field.getProperty().isPresent()) {
				return field.getProperty().map(CassandraPersistentProperty::getColumnName);
			}

			if (column.getColumnName().isPresent()) {
				return column.getColumnName().map(CqlIdentifier::cqlId);
			}

			return column.getCqlIdentifier();

		} catch (IllegalStateException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	/**
	 * @param entity
	 * @param key
	 * @return
	 */
	protected Field createPropertyField(CassandraPersistentEntity<?> entity, ColumnName key) {
		return entity == null ? new Field(key) : new MetadataBackedField(key, entity, mappingContext);
	}

	@SuppressWarnings("unchecked")
	TypeInformation<?> getTypeInformation(Field field, Optional<? extends Object> value) {

		return field.getProperty().map(CassandraPersistentProperty::getTypeInformation).orElseGet(() -> {

			return value.map(Object::getClass) //
					.map(ClassTypeInformation::from) //
					.orElse((ClassTypeInformation) ClassTypeInformation.OBJECT);

		});
	}

	/**
	 * Value object to represent a field and its meta-information.
	 *
	 * @author Mark Paluch
	 */
	protected static class Field {

		protected final ColumnName name;

		/**
		 * Creates a new {@link Field} without meta-information but the given name.
		 *
		 * @param name must not be {@literal null} or empty.
		 */
		public Field(ColumnName name) {

			Assert.notNull(name, "Name must not be null!");
			this.name = name;
		}

		/**
		 * Returns a new {@link Field} with the given name.
		 *
		 * @param name must not be {@literal null} or empty.
		 * @return
		 */
		public Field with(ColumnName name) {
			return new Field(name);
		}

		/**
		 * Returns the underlying {@link CassandraPersistentProperty} backing the field. For path traversals this will be
		 * the property that represents the value to handle. This means it'll be the leaf property for plain paths or the
		 * association property in case we refer to an association somewhere in the path.
		 *
		 * @return
		 */
		public Optional<CassandraPersistentProperty> getProperty() {
			return Optional.empty();
		}

		/**
		 * Returns the key to be used in the mapped document eventually.
		 *
		 * @return
		 */
		public ColumnName getMappedKey() {
			return name;
		}
	}

	/**
	 * Extension of {@link Field} to be backed with mapping metadata.
	 *
	 * @author Mark Paluch
	 */
	protected static class MetadataBackedField extends Field {

		private final CassandraPersistentEntity<?> entity;
		private final MappingContext<? extends CassandraPersistentEntity<?>, CassandraPersistentProperty> mappingContext;
		private final Optional<PersistentPropertyPath<CassandraPersistentProperty>> path;
		private final CassandraPersistentProperty property;
		private final Optional<CassandraPersistentProperty> optionalProperty;

		/**
		 * Creates a new {@link MetadataBackedField} with the given name, {@link MongoPersistentEntity} and
		 * {@link MappingContext}.
		 *
		 * @param name must not be {@literal null} or empty.
		 * @param entity must not be {@literal null}.
		 * @param context must not be {@literal null}.
		 */
		public MetadataBackedField(ColumnName name, CassandraPersistentEntity<?> entity,
				MappingContext<? extends CassandraPersistentEntity<?>, CassandraPersistentProperty> context) {
			this(name, entity, context, null);
		}

		/**
		 * Creates a new {@link MetadataBackedField} with the given name, {@link CassandraPersistentProperty} and
		 * {@link MappingContext} with the given {@link CassandraPersistentProperty}.
		 *
		 * @param name must not be {@literal null} or empty.
		 * @param entity must not be {@literal null}.
		 * @param context must not be {@literal null}.
		 * @param property may be {@literal null}.
		 */
		public MetadataBackedField(ColumnName name, CassandraPersistentEntity<?> entity,
				MappingContext<? extends CassandraPersistentEntity<?>, CassandraPersistentProperty> context,
				CassandraPersistentProperty property) {

			super(name);

			Assert.notNull(entity, "MongoPersistentEntity must not be null!");

			this.entity = entity;
			this.mappingContext = context;
			this.path = getPath(name.toCql());
			this.property = path.map(PersistentPropertyPath::getLeafProperty).orElse(property);
			this.optionalProperty = Optional.ofNullable(this.property);
		}

		/**
		 * Returns the {@link PersistentPropertyPath} for the given {@code pathExpression}.
		 *
		 * @param pathExpression
		 * @return
		 */
		private Optional<PersistentPropertyPath<CassandraPersistentProperty>> getPath(String pathExpression) {

			try {
				PropertyPath path = PropertyPath.from(pathExpression.replaceAll("\\.\\d", ""), entity.getTypeInformation());
				PersistentPropertyPath<CassandraPersistentProperty> propertyPath = mappingContext
						.getPersistentPropertyPath(path);

				return Optional.of(propertyPath);
			} catch (PropertyReferenceException e) {
				return Optional.empty();
			}
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.convert.QueryMapper.Field#with(java.lang.String)
		 */
		@Override
		public MetadataBackedField with(ColumnName name) {
			return new MetadataBackedField(name, entity, mappingContext, property);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.convert.QueryMapper.Field#getProperty()
		 */
		@Override
		public Optional<CassandraPersistentProperty> getProperty() {
			return optionalProperty;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.convert.QueryMapper.Field#getTargetKey()
		 */
		@Override
		public ColumnName getMappedKey() {

			return path.map(PersistentPropertyPath::getLeafProperty) //
					.map(CassandraPersistentProperty::getColumnName) //
					.map(ColumnName::from) //
					.orElse(name);
		}
	}
}
