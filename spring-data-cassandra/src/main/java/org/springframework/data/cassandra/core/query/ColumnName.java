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
package org.springframework.data.cassandra.core.query;

import java.util.Optional;

import org.springframework.cassandra.core.cql.CqlIdentifier;
import org.springframework.util.Assert;

/**
 * Value object representing a column name. Column names can be expressed either through {@link CqlIdentifier} or a
 * {@link String} literal. Using a String literal preserves case and is suitable to reference properties.
 * <p>
 * Equality and hash code derive from {@link #toCql()}.
 * <p>
 * Implementing classes must provide either {@link #getColumnName()} or {@link #getCqlIdentifier()}.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public abstract class ColumnName {

	/**
	 * Create a {@link ColumnName} given {@link CqlIdentifier}. The resulting instance uses CQL identifier rules to
	 * identify column names (quoting, case-sensitivity).
	 *
	 * @param cqlIdentifier must not be {@literal null}.
	 * @return the {@link ColumnName} for {@link CqlIdentifier}
	 * @see CqlIdentifier
	 */
	public static ColumnName from(CqlIdentifier cqlIdentifier) {

		Assert.notNull(cqlIdentifier, "Column name must not be null");

		return new CqlIdentifierColumnName(cqlIdentifier);
	}

	/**
	 * Create a {@link ColumnName} given a string {@code columnName}. The resulting instance uses String rules to identify
	 * column names (case-sensitivity).
	 *
	 * @param columnName must not be {@literal null} or empty.
	 * @return the {@link ColumnName} for {@link CqlIdentifier}
	 */
	public static ColumnName from(String columnName) {

		Assert.notNull(columnName, "Column name must not be null");
		Assert.hasText(columnName, "Column name must not be empty");

		return new StringColumnName(columnName);
	}

	/**
	 * @return the optional column name.
	 */
	public abstract Optional<String> getColumnName();

	/**
	 * @return the optional {@link CqlIdentifier}.
	 */
	public abstract Optional<CqlIdentifier> getCqlIdentifier();

	/**
	 * Represent the column name as CQL.
	 *
	 * @return CQL representation of the column name.
	 */
	public abstract String toCql();

	/* (non-Javadoc)
	 * @see org.springframework.data.cassandra.core.query.Criteria#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ColumnName))
			return false;

		ColumnName that = (ColumnName) o;

		return toCql().equals(that.toCql());
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return 31 + toCql().hashCode();
	}

	/**
	 * {@link String}-based column name representation. Preserves letter casing.
	 *
	 * @author Mark Paluch
	 */
	static class StringColumnName extends ColumnName {

		private final String columnName;

		StringColumnName(String columnName) {
			this.columnName = columnName;
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return columnName;
		}

		/* (non-Javadoc)
		 * @see org.springframework.data.cassandra.core.query.ColumnName#getColumnName()
		 */
		@Override
		public Optional<String> getColumnName() {
			return Optional.of(columnName);
		}

		/* (non-Javadoc)
		 * @see org.springframework.data.cassandra.core.query.ColumnName#getCqlIdentifier()
		 */
		@Override
		public Optional<CqlIdentifier> getCqlIdentifier() {
			return Optional.empty();
		}

		/* (non-Javadoc)
		 * @see org.springframework.data.cassandra.core.query.ColumnName#toCql()
		 */
		@Override
		public String toCql() {
			return columnName;
		}
	}

	/**
	 * {@link CqlIdentifier}-based column name representation. Follows {@link CqlIdentifier} comparison rules.
	 *
	 * @author Mark Paluch
	 */
	static class CqlIdentifierColumnName extends ColumnName {

		private final CqlIdentifier cqlIdentifier;

		CqlIdentifierColumnName(CqlIdentifier cqlIdentifier) {
			this.cqlIdentifier = cqlIdentifier;
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return cqlIdentifier.toString();
		}

		/* (non-Javadoc)
		 * @see org.springframework.data.cassandra.core.query.ColumnName#getColumnName()
		 */
		@Override
		public Optional<String> getColumnName() {
			return Optional.empty();
		}

		/* (non-Javadoc)
		 * @see org.springframework.data.cassandra.core.query.ColumnName#getCqlIdentifier()
		 */
		@Override
		public Optional<CqlIdentifier> getCqlIdentifier() {
			return Optional.of(cqlIdentifier);
		}

		/* (non-Javadoc)
		 * @see org.springframework.data.cassandra.core.query.ColumnName#toCql()
		 */
		@Override
		public String toCql() {
			return cqlIdentifier.toCql();
		}
	}
}
