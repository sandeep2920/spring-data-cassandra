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
package org.springframework.data.cassandra.core;

import static org.mockito.Mockito.*;

import lombok.Data;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cassandra.core.cql.CqlIdentifier;
import org.springframework.data.annotation.Id;
import org.springframework.data.cassandra.mapping.BasicCassandraMappingContext;
import org.springframework.data.cassandra.mapping.Table;
import org.springframework.data.cassandra.mapping.UserDefinedType;

import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.TableMetadata;
import com.datastax.driver.core.UserType;

/**
 * Unit tests for {@link CassandraPersistentEntitySchemaDropper}.
 *
 * @author Mark Paluch.
 */
@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class CassandraPersistentEntitySchemaDropperUnitTests {

	@Mock CassandraAdminOperations operations;
	@Mock KeyspaceMetadata metadata;
	@Mock UserType universetype;
	@Mock UserType moontype;
	@Mock UserType planettype;
	@Mock TableMetadata person;
	@Mock TableMetadata contact;

	BasicCassandraMappingContext context = new BasicCassandraMappingContext();

	// DATACASS-355
	@Before
	public void setUp() throws Exception {

		context.setUserTypeResolver(typeName -> metadata.getUserType(typeName.toCql()));

		when(operations.getKeyspaceMetadata()).thenReturn(metadata);
		when(universetype.getTypeName()).thenReturn("universetype");
		when(moontype.getTypeName()).thenReturn("moontype");
		when(planettype.getTypeName()).thenReturn("planettype");
		when(person.getName()).thenReturn("person");
		when(contact.getName()).thenReturn("contact");
	}

	@Test // DATACASS-355
	public void shouldDropTypes() throws Exception {

		context.setInitialEntitySet(new HashSet<>(Arrays.asList(MoonType.class, UniverseType.class)));
		context.afterPropertiesSet();

		when(metadata.getUserTypes()).thenReturn(Arrays.asList(universetype, moontype, planettype));

		CassandraPersistentEntitySchemaDropper schemaDropper = new CassandraPersistentEntitySchemaDropper(context,
				operations);

		schemaDropper.dropUserTypes(true);

		verify(operations).dropUserType(CqlIdentifier.cqlId("universetype"));
		verify(operations).dropUserType(CqlIdentifier.cqlId("moontype"));
		verify(operations).dropUserType(CqlIdentifier.cqlId("planettype"));
		verify(operations).getKeyspaceMetadata();
		verifyNoMoreInteractions(operations);
	}

	@Test // DATACASS-355
	public void dropUserTypesShouldRetainUnusedTypes() {

		context.setInitialEntitySet(new HashSet<>(Arrays.asList(MoonType.class, UniverseType.class)));
		context.afterPropertiesSet();

		when(metadata.getUserTypes()).thenReturn(Arrays.asList(universetype, moontype, planettype));

		CassandraPersistentEntitySchemaDropper schemaDropper = new CassandraPersistentEntitySchemaDropper(context,
				operations);

		schemaDropper.dropUserTypes(false);

		verify(operations).dropUserType(CqlIdentifier.cqlId("universetype"));
		verify(operations).dropUserType(CqlIdentifier.cqlId("moontype"));
		verify(operations).getKeyspaceMetadata();
		verifyNoMoreInteractions(operations);
	}

	@Test // DATACASS-355
	public void shouldDropTables() throws Exception {

		context.setInitialEntitySet(Collections.singleton(Person.class));
		context.afterPropertiesSet();

		when(metadata.getTables()).thenReturn(Arrays.asList(person, contact));

		CassandraPersistentEntitySchemaDropper schemaDropper = new CassandraPersistentEntitySchemaDropper(context,
				operations);

		schemaDropper.dropTables(true);

		verify(operations).dropTable(CqlIdentifier.cqlId("person"));
		verify(operations).dropTable(CqlIdentifier.cqlId("contact"));
		verify(operations).getKeyspaceMetadata();
		verifyNoMoreInteractions(operations);
	}

	@Test
	public void dropTablesShouldRetainUnusedTables() throws Exception {

		context.setInitialEntitySet(Collections.singleton(Person.class));
		context.afterPropertiesSet();

		when(metadata.getTables()).thenReturn(Arrays.asList(person, contact));

		CassandraPersistentEntitySchemaDropper schemaDropper = new CassandraPersistentEntitySchemaDropper(context,
				operations);

		schemaDropper.dropTables(false);

		verify(operations).dropTable(CqlIdentifier.cqlId("person"));
		verify(operations).getKeyspaceMetadata();
		verifyNoMoreInteractions(operations);
	}

	@UserDefinedType
	@Data
	static class UniverseType {}

	@UserDefinedType
	static class MoonType {}

	@UserDefinedType
	static class PlanetType {}

	@Table
	static class Person {
		@Id String id;
	}
}
