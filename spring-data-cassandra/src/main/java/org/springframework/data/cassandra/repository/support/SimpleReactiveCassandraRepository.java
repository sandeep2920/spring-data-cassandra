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
package org.springframework.data.cassandra.repository.support;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.Serializable;

import org.reactivestreams.Publisher;
import org.springframework.data.cassandra.core.ReactiveCassandraOperations;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.data.cassandra.repository.query.CassandraEntityInformation;
import org.springframework.util.Assert;

import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;

/**
 * Reactive repository base implementation for Cassandra.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class SimpleReactiveCassandraRepository<T, ID extends Serializable>
		implements ReactiveCassandraRepository<T, ID> {

	protected CassandraEntityInformation<T, ID> entityInformation;

	protected ReactiveCassandraOperations operations;

	private final boolean isPrimaryKeyEntity;

	/**
	 * Create a new {@link SimpleReactiveCassandraRepository} for the given {@link CassandraEntityInformation} and
	 * {@link ReactiveCassandraOperations}.
	 *
	 * @param metadata must not be {@literal null}.
	 * @param operations must not be {@literal null}.
	 */
	public SimpleReactiveCassandraRepository(CassandraEntityInformation<T, ID> metadata,
			ReactiveCassandraOperations operations) {

		Assert.notNull(metadata, "CassandraEntityInformation must not be null");
		Assert.notNull(operations, "ReactiveCassandraOperations must not be null");

		this.entityInformation = metadata;
		this.operations = operations;
		this.isPrimaryKeyEntity = metadata.isPrimaryKeyEntity();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#save(S)
	 */
	@Override
	public <S extends T> Mono<S> save(S entity) {

		Assert.notNull(entity, "Entity must not be null");

		if (entityInformation.isNew(entity) || isPrimaryKeyEntity) {
			return operations.insert(entity);
		}

		return operations.update(entity);

	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#save(java.lang.Iterable)
	 */
	@Override
	public <S extends T> Flux<S> save(Iterable<S> entities) {

		Assert.notNull(entities, "The given Iterable of entities must not be null");

		return save(Flux.fromIterable(entities));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#save(org.reactivestreams.Publisher)
	 */
	@Override
	public <S extends T> Flux<S> save(Publisher<S> entityStream) {

		Assert.notNull(entityStream, "The given Publisher of entities must not be null");

		return Flux.from(entityStream).flatMap(entity -> {

			if (entityInformation.isNew(entity) || isPrimaryKeyEntity) {
				return operations.insert(entity);
			}

			return operations.update(entity);
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cassandra.repository.ReactiveCassandraRepository#insert(java.lang.Object)
	 */
	@Override
	public <S extends T> Mono<S> insert(S entity) {

		Assert.notNull(entity, "Entity must not be null");

		return operations.insert(entity);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cassandra.repository.ReactiveCassandraRepository#insert(java.lang.Iterable)
	 */
	@Override
	public <S extends T> Flux<S> insert(Iterable<S> entities) {

		Assert.notNull(entities, "The given Iterable of entities must not be null");

		return operations.insert(Flux.fromIterable(entities));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cassandra.repository.ReactiveCassandraRepository#insert(org.reactivestreams.Publisher)
	 */
	@Override
	public <S extends T> Flux<S> insert(Publisher<S> entityStream) {

		Assert.notNull(entityStream, "The given Publisher of entities must not be null");

		return operations.insert(entityStream);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#findOne(java.io.Serializable)
	 */
	@Override
	public Mono<T> findOne(ID id) {

		Assert.notNull(id, "The given id must not be null");

		return operations.selectOneById(id, entityInformation.getJavaType());
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#findOne(reactor.core.publisher.Mono)
	 */
	@Override
	public Mono<T> findOne(Mono<ID> mono) {

		Assert.notNull(mono, "The given id must not be null");

		return mono.flatMap(id -> operations.selectOneById(id, entityInformation.getJavaType()));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#exists(java.io.Serializable)
	 */
	@Override
	public Mono<Boolean> exists(ID id) {

		Assert.notNull(id, "The given id must not be null");

		return operations.exists(id, entityInformation.getJavaType());
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#exists(reactor.core.publisher.Mono)
	 */
	@Override
	public Mono<Boolean> exists(Mono<ID> mono) {

		Assert.notNull(mono, "The given id must not be null");

		return mono.flatMap(id -> operations.exists(id, entityInformation.getJavaType()));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#findAll()
	 */
	@Override
	public Flux<T> findAll() {

		Select select = QueryBuilder.select().from(entityInformation.getTableName().toCql());
		return operations.select(select, entityInformation.getJavaType());
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#findAll(java.lang.Iterable)
	 */
	@Override
	public Flux<T> findAll(Iterable<ID> iterable) {

		Assert.notNull(iterable, "The given Iterable of id's must not be null");

		return findAll(Flux.fromIterable(iterable));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#findAll(org.reactivestreams.Publisher)
	 */
	@Override
	public Flux<T> findAll(Publisher<ID> idStream) {

		Assert.notNull(idStream, "The given Publisher of id's must not be null");

		return Flux.from(idStream).flatMap(id -> operations.selectOneById(id, entityInformation.getJavaType()));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#count()
	 */
	@Override
	public Mono<Long> count() {
		return operations.count(entityInformation.getJavaType());
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#delete(java.io.Serializable)
	 */
	@Override
	public Mono<Void> delete(ID id) {

		Assert.notNull(id, "The given id must not be null");

		return operations.deleteById(id, entityInformation.getJavaType()).then();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#delete(java.lang.Object)
	 */
	@Override
	public Mono<Void> delete(T entity) {

		Assert.notNull(entity, "The given entity must not be null");

		return operations.delete(entity).then();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#delete(java.lang.Iterable)
	 */
	@Override
	public Mono<Void> delete(Iterable<? extends T> entities) {

		Assert.notNull(entities, "The given Iterable of entities must not be null");

		return operations.delete(Flux.fromIterable(entities)).then();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#delete(org.reactivestreams.Publisher)
	 */
	@Override
	public Mono<Void> delete(Publisher<? extends T> entityStream) {

		Assert.notNull(entityStream, "The given Publisher of entities must not be null");

		return operations.delete(entityStream).then();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#deleteAll()
	 */
	@Override
	public Mono<Void> deleteAll() {
		return operations.truncate(entityInformation.getJavaType());
	}
}
