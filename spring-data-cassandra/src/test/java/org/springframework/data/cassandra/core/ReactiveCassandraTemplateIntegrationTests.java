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
package org.springframework.data.cassandra.core;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import org.junit.Before;
import org.junit.Test;
import org.springframework.cassandra.core.ReactiveCqlTemplate;
import org.springframework.cassandra.core.session.DefaultBridgedReactiveSession;
import org.springframework.cassandra.test.integration.AbstractKeyspaceCreatingIntegrationTest;
import org.springframework.data.cassandra.convert.MappingCassandraConverter;
import org.springframework.data.cassandra.core.query.ChainedCriteria;
import org.springframework.data.cassandra.core.query.Columns;
import org.springframework.data.cassandra.core.query.Criteria;
import org.springframework.data.cassandra.core.query.Query;
import org.springframework.data.cassandra.domain.Person;
import org.springframework.data.cassandra.domain.UserToken;
import org.springframework.data.cassandra.test.integration.support.SchemaTestUtils;
import org.springframework.data.domain.Sort;

import com.datastax.driver.core.utils.UUIDs;

/**
 * Integration tests for {@link ReactiveCassandraTemplate}.
 *
 * @author Mark Paluch
 */
public class ReactiveCassandraTemplateIntegrationTests extends AbstractKeyspaceCreatingIntegrationTest {

	ReactiveCassandraTemplate template;

	@Before
	public void setUp() throws Exception {

		MappingCassandraConverter converter = new MappingCassandraConverter();
		CassandraTemplate cassandraTemplate = new CassandraTemplate(this.session, converter);
		DefaultBridgedReactiveSession session = new DefaultBridgedReactiveSession(this.session, Schedulers.elastic());

		template = new ReactiveCassandraTemplate(new ReactiveCqlTemplate(session), converter);

		SchemaTestUtils.potentiallyCreateTableFor(Person.class, cassandraTemplate);
		SchemaTestUtils.potentiallyCreateTableFor(UserToken.class, cassandraTemplate);
		SchemaTestUtils.truncate(Person.class, cassandraTemplate);
		SchemaTestUtils.truncate(UserToken.class, cassandraTemplate);
	}

	@Test // DATACASS-335
	public void insertShouldInsertEntity() {

		Person person = new Person("heisenberg", "Walter", "White");

		Mono<Person> insert = template.insert(person);
		StepVerifier.create(template.selectOneById(person.getId(), Person.class)).verifyComplete();

		StepVerifier.create(insert).expectNext(person).verifyComplete();

		StepVerifier.create(template.selectOneById(person.getId(), Person.class)).expectNext(person).verifyComplete();
	}

	@Test // DATACASS-335
	public void shouldInsertAndCountEntities() {

		Person person = new Person("heisenberg", "Walter", "White");

		StepVerifier.create(template.insert(person)).expectNextCount(1).verifyComplete();

		StepVerifier.create(template.count(Person.class)).expectNext(1L).verifyComplete();
	}

	@Test // DATACASS-335
	public void updateShouldUpdateEntity() {

		Person person = new Person("heisenberg", "Walter", "White");

		StepVerifier.create(template.insert(person)).expectNextCount(1).verifyComplete();

		person.setFirstname("Walter Hartwell");

		StepVerifier.create(template.insert(person)).expectNextCount(1).verifyComplete();

		StepVerifier.create(template.selectOneById(person.getId(), Person.class)).expectNext(person).verifyComplete();
	}

	@Test // DATACASS-343
	public void deleteByQueryShouldRemoveEntity() {

		Person person = new Person("heisenberg", "Walter", "White");
		template.insert(person).block();

		Query query = Query.from(Criteria.where("id").is("heisenberg"));
		assertThat(template.delete(query, Person.class).block()).isTrue();

		assertThat(template.selectOneById(person.getId(), Person.class).block()).isNull();
	}

	@Test // DATACASS-343
	public void deleteColumnsByQueryShouldRemoveColumn() {

		Person person = new Person("heisenberg", "Walter", "White");
		template.insert(person).block();

		Query query = Query.from(Criteria.where("id").is("heisenberg"));
		query.with(Columns.from("lastname"));

		assertThat(template.delete(query, Person.class).block()).isTrue();

		Person loaded = template.selectOneById(person.getId(), Person.class).block();
		assertThat(loaded.getFirstname()).isEqualTo("Walter");
		assertThat(loaded.getLastname()).isNull();
	}

	@Test // DATACASS-335
	public void deleteShouldRemoveEntity() {

		Person person = new Person("heisenberg", "Walter", "White");

		StepVerifier.create(template.insert(person)).expectNextCount(1).verifyComplete();

		StepVerifier.create(template.delete(person)).expectNext(person).verifyComplete();

		StepVerifier.create(template.selectOneById(person.getId(), Person.class)).verifyComplete();
	}

	@Test // DATACASS-335
	public void deleteByIdShouldRemoveEntity() {

		Person person = new Person("heisenberg", "Walter", "White");

		StepVerifier.create(template.insert(person)).expectNextCount(1).verifyComplete();

		StepVerifier.create(template.deleteById(person.getId(), Person.class)).expectNext(true).verifyComplete();

		StepVerifier.create(template.selectOneById(person.getId(), Person.class)).verifyComplete();
	}

	@Test // DATACASS-343
	public void shouldSelectByQueryWithSorting() {

		UserToken token1 = new UserToken();
		token1.setUserId(UUIDs.endOf(System.currentTimeMillis()));
		token1.setToken(UUIDs.startOf(System.currentTimeMillis()));
		token1.setUserComment("foo");

		UserToken token2 = new UserToken();
		token2.setUserId(token1.getUserId());
		token2.setToken(UUIDs.endOf(System.currentTimeMillis() + 100));
		token2.setUserComment("bar");

		template.insert(token1).block();
		template.insert(token2).block();

		Query query = Query.from(ChainedCriteria.where("userId").is(token1.getUserId())).with(new Sort("token"));

		assertThat(template.select(query, UserToken.class).collectList().block()).containsSequence(token1, token2);
	}

	@Test // DATACASS-343
	public void shouldSelectOneByQuery() {

		UserToken token1 = new UserToken();
		token1.setUserId(UUIDs.endOf(System.currentTimeMillis()));
		token1.setToken(UUIDs.startOf(System.currentTimeMillis()));
		token1.setUserComment("foo");

		template.insert(token1).block();

		Query query = Query.from(Criteria.where("userId").is(token1.getUserId()));

		assertThat(template.selectOne(query, UserToken.class).block()).isEqualTo(token1);
	}
}
