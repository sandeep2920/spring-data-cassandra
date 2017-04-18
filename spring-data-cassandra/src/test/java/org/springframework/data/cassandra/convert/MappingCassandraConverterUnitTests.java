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
package org.springframework.data.cassandra.convert;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assume.*;
import static org.mockito.Mockito.*;
import static org.springframework.data.cassandra.RowMockUtil.*;
import static org.springframework.data.cassandra.repository.support.BasicMapId.*;

import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cassandra.core.PrimaryKeyType;
import org.springframework.core.SpringVersion;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.data.cassandra.RowMockUtil;
import org.springframework.data.cassandra.domain.CompositeKey;
import org.springframework.data.cassandra.domain.Person;
import org.springframework.data.cassandra.domain.TypeWithCompositeKey;
import org.springframework.data.cassandra.domain.TypeWithKeyClass;
import org.springframework.data.cassandra.domain.TypeWithMapId;
import org.springframework.data.cassandra.domain.UserToken;
import org.springframework.data.cassandra.mapping.BasicCassandraMappingContext;
import org.springframework.data.cassandra.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.mapping.CassandraType;
import org.springframework.data.cassandra.mapping.PrimaryKey;
import org.springframework.data.cassandra.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.mapping.Table;
import org.springframework.data.util.Version;
import org.springframework.test.util.ReflectionTestUtils;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.DataType.Name;
import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.querybuilder.Assignment;
import com.datastax.driver.core.querybuilder.BuiltStatement;
import com.datastax.driver.core.querybuilder.Clause;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Delete.Where;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Update;
import com.datastax.driver.core.querybuilder.Update.Assignments;

/**
 * Unit tests for {@link MappingCassandraConverter}.
 *
 * @author Mark Paluch
 * @soundtrack Outlandich - Dont Leave Me Feat Cyt (Sun Kidz Electrocore Mix)
 */
@SuppressWarnings("Since15")
@RunWith(MockitoJUnitRunner.class)
public class MappingCassandraConverterUnitTests {

	private static final Version VERSION_4_3 = Version.parse("4.3");

	@Rule public final ExpectedException expectedException = ExpectedException.none();

	@Mock Row rowMock;

	CassandraMappingContext mappingContext;
	MappingCassandraConverter mappingCassandraConverter;

	@Before
	public void setUp() throws Exception {

		mappingContext = new BasicCassandraMappingContext();

		mappingCassandraConverter = new MappingCassandraConverter(mappingContext);
		mappingCassandraConverter.afterPropertiesSet();
	}

	@Test // DATACASS-260
	public void insertEnumShouldMapToString() {

		WithEnumColumns withEnumColumns = new WithEnumColumns();

		withEnumColumns.setCondition(Condition.MINT);

		Insert insert = QueryBuilder.insertInto("table");

		mappingCassandraConverter.write(withEnumColumns, insert);

		assertThat(getValues(insert)).contains("MINT");
	}

	@Test // DATACASS-260
	public void insertEnumDoesNotMapToOrdinalBeforeSpring43() {

		assumeTrue(Version.parse(SpringVersion.getVersion()).isLessThan(VERSION_4_3));

		expectedException.expect(ConverterNotFoundException.class);

		UnsupportedEnumToOrdinalMapping unsupportedEnumToOrdinalMapping = new UnsupportedEnumToOrdinalMapping();
		unsupportedEnumToOrdinalMapping.setAsOrdinal(Condition.MINT);

		Insert insert = QueryBuilder.insertInto("table");

		mappingCassandraConverter.write(unsupportedEnumToOrdinalMapping, insert);
	}

	@Test // DATACASS-255
	public void insertEnumMapsToOrdinalWithSpring43AndHiger() {

		assumeTrue(Version.parse(SpringVersion.getVersion()).isGreaterThanOrEqualTo(VERSION_4_3));

		UnsupportedEnumToOrdinalMapping unsupportedEnumToOrdinalMapping = new UnsupportedEnumToOrdinalMapping();
		unsupportedEnumToOrdinalMapping.setAsOrdinal(Condition.USED);

		Insert insert = QueryBuilder.insertInto("table");

		mappingCassandraConverter.write(unsupportedEnumToOrdinalMapping, insert);

		assertThat(getValues(insert)).contains((Object) Integer.valueOf(Condition.USED.ordinal()));
	}

	@Test // DATACASS-260
	public void insertEnumAsPrimaryKeyShouldMapToString() {

		EnumPrimaryKey key = new EnumPrimaryKey();
		key.setCondition(Condition.MINT);

		Insert insert = QueryBuilder.insertInto("table");

		mappingCassandraConverter.write(key, insert);

		assertThat(getValues(insert)).contains((Object) "MINT");
	}

	@Test // DATACASS-260
	public void insertEnumInCompositePrimaryKeyShouldMapToString() {

		EnumCompositePrimaryKey key = new EnumCompositePrimaryKey();
		key.setCondition(Condition.MINT);

		CompositeKeyThing composite = new CompositeKeyThing();
		composite.setKey(key);

		Insert insert = QueryBuilder.insertInto("table");

		mappingCassandraConverter.write(composite, insert);

		assertThat(getValues(insert)).contains((Object) "MINT");
	}

	@Test // DATACASS-260
	public void updateEnumShouldMapToString() {

		WithEnumColumns withEnumColumns = new WithEnumColumns();
		withEnumColumns.setCondition(Condition.MINT);

		Update update = QueryBuilder.update("table");

		mappingCassandraConverter.write(withEnumColumns, update);

		assertThat(getAssignmentValues(update)).contains((Object) "MINT");
	}

	@Test // DATACASS-260
	public void updateEnumAsPrimaryKeyShouldMapToString() {

		EnumPrimaryKey key = new EnumPrimaryKey();
		key.setCondition(Condition.MINT);

		Update update = QueryBuilder.update("table");

		mappingCassandraConverter.write(key, update);

		assertThat(getWhereValues(update)).contains((Object) "MINT");
	}

	@Test // DATACASS-260
	public void updateEnumInCompositePrimaryKeyShouldMapToString() {

		EnumCompositePrimaryKey key = new EnumCompositePrimaryKey();
		key.setCondition(Condition.MINT);

		CompositeKeyThing composite = new CompositeKeyThing();
		composite.setKey(key);

		Update update = QueryBuilder.update("table");

		mappingCassandraConverter.write(composite, update);

		assertThat(getWhereValues(update)).contains((Object) "MINT");
	}

	@Test // DATACASS-260
	public void whereEnumAsPrimaryKeyShouldMapToString() {

		EnumPrimaryKey key = new EnumPrimaryKey();
		key.setCondition(Condition.MINT);

		Where where = QueryBuilder.delete().from("table").where();

		mappingCassandraConverter.write(key, where);

		assertThat(getWhereValues(where)).contains((Object) "MINT");
	}

	@Test // DATACASS-260
	public void whereEnumInCompositePrimaryKeyShouldMapToString() {

		EnumCompositePrimaryKey key = new EnumCompositePrimaryKey();
		key.setCondition(Condition.MINT);

		CompositeKeyThing composite = new CompositeKeyThing();
		composite.setKey(key);

		Where where = QueryBuilder.delete().from("table").where();

		mappingCassandraConverter.write(composite, where);

		assertThat(getWhereValues(where)).contains((Object) "MINT");
	}

	@Test // DATACASS-280
	public void shouldReadStringCorrectly() {

		when(rowMock.getString(0)).thenReturn("foo");

		String result = mappingCassandraConverter.readRow(String.class, rowMock);

		assertThat(result).isEqualTo("foo");
	}

	@Test // DATACASS-280
	public void shouldReadIntegerCorrectly() {

		when(rowMock.getObject(0)).thenReturn(2);

		Integer result = mappingCassandraConverter.readRow(Integer.class, rowMock);

		assertThat(result).isEqualTo(2);
	}

	@Test // DATACASS-280
	public void shouldReadLongCorrectly() {

		when(rowMock.getObject(0)).thenReturn(2);

		Long result = mappingCassandraConverter.readRow(Long.class, rowMock);

		assertThat(result).isEqualTo(2L);
	}

	@Test // DATACASS-280
	public void shouldReadDoubleCorrectly() {

		when(rowMock.getObject(0)).thenReturn(2D);

		Double result = mappingCassandraConverter.readRow(Double.class, rowMock);

		assertThat(result).isEqualTo(2D);
	}

	@Test // DATACASS-280
	public void shouldReadFloatCorrectly() {

		when(rowMock.getObject(0)).thenReturn(2F);

		Float result = mappingCassandraConverter.readRow(Float.class, rowMock);

		assertThat(result).isEqualTo(2F);
	}

	@Test // DATACASS-280
	public void shouldReadBigIntegerCorrectly() {

		when(rowMock.getObject(0)).thenReturn(BigInteger.valueOf(2));

		BigInteger result = mappingCassandraConverter.readRow(BigInteger.class, rowMock);

		assertThat(result).isEqualTo(BigInteger.valueOf(2));
	}

	@Test // DATACASS-280
	public void shouldReadBigDecimalCorrectly() {

		when(rowMock.getObject(0)).thenReturn(BigDecimal.valueOf(2));

		BigDecimal result = mappingCassandraConverter.readRow(BigDecimal.class, rowMock);

		assertThat(result).isEqualTo(BigDecimal.valueOf(2));
	}

	@Test // DATACASS-280
	public void shouldReadUUIDCorrectly() {

		UUID uuid = UUID.randomUUID();

		when(rowMock.getUUID(0)).thenReturn(uuid);

		UUID result = mappingCassandraConverter.readRow(UUID.class, rowMock);

		assertThat(result).isEqualTo(uuid);
	}

	@Test // DATACASS-280
	public void shouldReadInetAddressCorrectly() throws UnknownHostException {

		InetAddress localHost = InetAddress.getLocalHost();

		when(rowMock.getInet(0)).thenReturn(localHost);

		InetAddress result = mappingCassandraConverter.readRow(InetAddress.class, rowMock);

		assertThat(result).isEqualTo(localHost);
	}

	@Test // DATACASS-280, DATACASS-271
	public void shouldReadTimestampCorrectly() {

		Date date = new Date(1);

		when(rowMock.getTimestamp(0)).thenReturn(date);

		Date result = mappingCassandraConverter.readRow(Date.class, rowMock);

		assertThat(result).isEqualTo(date);
	}

	@Test // DATACASS-271
	public void shouldReadDateCorrectly() {

		LocalDate date = LocalDate.fromDaysSinceEpoch(1234);

		when(rowMock.getDate(0)).thenReturn(date);

		LocalDate result = mappingCassandraConverter.readRow(LocalDate.class, rowMock);

		assertThat(result).isEqualTo(date);
	}

	@Test // DATACASS-280
	public void shouldReadBooleanCorrectly() {

		when(rowMock.getBool(0)).thenReturn(true);

		Boolean result = mappingCassandraConverter.readRow(Boolean.class, rowMock);

		assertThat(result).isEqualTo(true);
	}

	@Test // DATACASS-296
	public void shouldReadLocalDateCorrectly() {

		LocalDateTime now = LocalDateTime.now();
		Instant instant = now.toInstant(ZoneOffset.UTC);

		Row rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataType.ascii()),
				column("localdate", Date.from(instant), DataType.timestamp()));

		TypeWithLocalDate result = mappingCassandraConverter.readRow(TypeWithLocalDate.class, rowMock);

		assertThat(result.localDate).isNotNull();
		assertThat(result.localDate.getYear()).isEqualTo(now.getYear());
		assertThat(result.localDate.getMonthValue()).isEqualTo(now.getMonthValue());
	}

	@Test // DATACASS-296
	public void shouldCreateInsertWithLocalDateCorrectly() {

		java.time.LocalDate now = java.time.LocalDate.now();

		TypeWithLocalDate typeWithLocalDate = new TypeWithLocalDate();
		typeWithLocalDate.localDate = now;

		Insert insert = QueryBuilder.insertInto("table");

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		assertThat(getValues(insert))
				.contains(LocalDate.fromYearMonthDay(now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
	}

	@Test // DATACASS-296
	public void shouldCreateUpdateWithLocalDateCorrectly() {

		java.time.LocalDate now = java.time.LocalDate.now();

		TypeWithLocalDate typeWithLocalDate = new TypeWithLocalDate();
		typeWithLocalDate.localDate = now;

		Update update = QueryBuilder.update("table");

		mappingCassandraConverter.write(typeWithLocalDate, update);

		assertThat(getAssignmentValues(update))
				.contains(LocalDate.fromYearMonthDay(now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
	}

	@Test // DATACASS-296
	public void shouldCreateInsertWithLocalDateListUsingCassandraDate() {

		java.time.LocalDate now = java.time.LocalDate.now();
		java.time.LocalDate localDate = java.time.LocalDate.of(2010, 7, 4);

		TypeWithLocalDate typeWithLocalDate = new TypeWithLocalDate();
		typeWithLocalDate.list = Arrays.asList(now, localDate);

		Insert insert = QueryBuilder.insertInto("table");

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		List<LocalDate> dates = getListValue(insert);

		assertThat(dates).contains(LocalDate.fromYearMonthDay(now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
		assertThat(dates).contains(LocalDate.fromYearMonthDay(2010, 7, 4));
	}

	@Test // DATACASS-296
	public void shouldCreateInsertWithLocalDateSetUsingCassandraDate() {

		java.time.LocalDate now = java.time.LocalDate.now();
		java.time.LocalDate localDate = java.time.LocalDate.of(2010, 7, 4);

		TypeWithLocalDate typeWithLocalDate = new TypeWithLocalDate();
		typeWithLocalDate.set = new HashSet<>(Arrays.asList(now, localDate));

		Insert insert = QueryBuilder.insertInto("table");

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		Set<LocalDate> dates = getSetValue(insert);

		assertThat(dates).contains(LocalDate.fromYearMonthDay(now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
		assertThat(dates).contains(LocalDate.fromYearMonthDay(2010, 7, 4));
	}

	@Test // DATACASS-296
	public void shouldReadLocalDateTimeUsingCassandraDateCorrectly() {

		Row rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataType.ascii()),
				column("localDate", LocalDate.fromYearMonthDay(2010, 7, 4), DataType.date()));

		TypeWithLocalDateMappedToDate result = mappingCassandraConverter.readRow(TypeWithLocalDateMappedToDate.class,
				rowMock);

		assertThat(result.localDate).isNotNull();
		assertThat(result.localDate.getYear()).isEqualTo(2010);
		assertThat(result.localDate.getMonthValue()).isEqualTo(7);
		assertThat(result.localDate.getDayOfMonth()).isEqualTo(4);
	}

	@Test // DATACASS-296, DATACASS-400
	public void shouldCreateInsertWithLocalDateUsingCassandraDateCorrectly() {

		TypeWithLocalDateMappedToDate typeWithLocalDate = new TypeWithLocalDateMappedToDate(null,
				java.time.LocalDate.of(2010, 7, 4));

		Insert insert = QueryBuilder.insertInto("table");

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		assertThat(getValues(insert).contains(LocalDate.fromYearMonthDay(2010, 7, 4))).isTrue();
	}

	@Test // DATACASS-296
	public void shouldCreateUpdateWithLocalDateUsingCassandraDateCorrectly() {

		TypeWithLocalDateMappedToDate typeWithLocalDate = new TypeWithLocalDateMappedToDate(null,
				java.time.LocalDate.of(2010, 7, 4));

		Update update = QueryBuilder.update("table");

		mappingCassandraConverter.write(typeWithLocalDate, update);

		assertThat(getAssignmentValues(update)).contains(LocalDate.fromYearMonthDay(2010, 7, 4));
	}

	@Test // DATACASS-296
	public void shouldReadLocalDateTimeCorrectly() {

		LocalDateTime now = LocalDateTime.now();
		Instant instant = now.toInstant(ZoneOffset.UTC);

		Row rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataType.ascii()),
				column("localDateTime", Date.from(instant), DataType.timestamp()));

		TypeWithLocalDate result = mappingCassandraConverter.readRow(TypeWithLocalDate.class, rowMock);

		assertThat(result.localDateTime).isNotNull();
		assertThat(result.localDateTime.getYear()).isEqualTo(now.getYear());
		assertThat(result.localDateTime.getMinute()).isEqualTo(now.getMinute());
	}

	@Test // DATACASS-296
	public void shouldReadInstantCorrectly() {

		LocalDateTime now = LocalDateTime.now();
		Instant instant = now.toInstant(ZoneOffset.UTC);

		Row rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataType.ascii()),
				column("instant", Date.from(instant), DataType.timestamp()));

		TypeWithInstant result = mappingCassandraConverter.readRow(TypeWithInstant.class, rowMock);

		assertThat(result.instant).isNotNull();
		assertThat(result.instant.getEpochSecond()).isEqualTo(instant.getEpochSecond());
	}

	@Test // DATACASS-296
	public void shouldReadZoneIdCorrectly() {

		Row rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataType.ascii()),
				column("zoneId", "Europe/Paris", DataType.varchar()));

		TypeWithZoneId result = mappingCassandraConverter.readRow(TypeWithZoneId.class, rowMock);

		assertThat(result.zoneId).isNotNull();
		assertThat(result.zoneId.getId()).isEqualTo("Europe/Paris");
	}

	@Test // DATACASS-296
	public void shouldReadJodaLocalDateTimeUsingCassandraDateCorrectly() {

		Row rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataType.ascii()),
				column("localDate", LocalDate.fromYearMonthDay(2010, 7, 4), DataType.date()));

		TypeWithJodaLocalDateMappedToDate result = mappingCassandraConverter
				.readRow(TypeWithJodaLocalDateMappedToDate.class, rowMock);

		assertThat(result.localDate).isNotNull();
		assertThat(result.localDate.getYear()).isEqualTo(2010);
		assertThat(result.localDate.getMonthOfYear()).isEqualTo(7);
		assertThat(result.localDate.getDayOfMonth()).isEqualTo(4);
	}

	@Test // DATACASS-296
	public void shouldCreateInsertWithJodaLocalDateUsingCassandraDateCorrectly() {

		TypeWithJodaLocalDateMappedToDate typeWithLocalDate = new TypeWithJodaLocalDateMappedToDate();
		typeWithLocalDate.localDate = new org.joda.time.LocalDate(2010, 7, 4);

		Insert insert = QueryBuilder.insertInto("table");

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		assertThat(getValues(insert).contains(LocalDate.fromYearMonthDay(2010, 7, 4))).isTrue();
	}

	@Test // DATACASS-296
	public void shouldCreateUpdateWithJodaLocalDateUsingCassandraDateCorrectly() {

		TypeWithJodaLocalDateMappedToDate typeWithLocalDate = new TypeWithJodaLocalDateMappedToDate();
		typeWithLocalDate.localDate = new org.joda.time.LocalDate(2010, 7, 4);

		Update update = QueryBuilder.update("table");

		mappingCassandraConverter.write(typeWithLocalDate, update);

		assertThat(getAssignmentValues(update)).contains(LocalDate.fromYearMonthDay(2010, 7, 4));
	}

	@Test // DATACASS-296
	public void shouldReadThreeTenBpLocalDateTimeUsingCassandraDateCorrectly() {

		Row rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataType.ascii()),
				column("localDate", LocalDate.fromYearMonthDay(2010, 7, 4), DataType.date()));

		TypeWithThreeTenBpLocalDateMappedToDate result = mappingCassandraConverter
				.readRow(TypeWithThreeTenBpLocalDateMappedToDate.class, rowMock);

		assertThat(result.localDate).isNotNull();
		assertThat(result.localDate.getYear()).isEqualTo(2010);
		assertThat(result.localDate.getMonthValue()).isEqualTo(7);
		assertThat(result.localDate.getDayOfMonth()).isEqualTo(4);
	}

	@Test // DATACASS-296
	public void shouldCreateInsertWithThreeTenBpLocalDateUsingCassandraDateCorrectly() {

		TypeWithThreeTenBpLocalDateMappedToDate typeWithLocalDate = new TypeWithThreeTenBpLocalDateMappedToDate();
		typeWithLocalDate.localDate = org.threeten.bp.LocalDate.of(2010, 7, 4);

		Insert insert = QueryBuilder.insertInto("table");

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		assertThat(getValues(insert).contains(LocalDate.fromYearMonthDay(2010, 7, 4))).isTrue();
	}

	@Test // DATACASS-296
	public void shouldCreateUpdateWithThreeTenBpLocalDateUsingCassandraDateCorrectly() {

		TypeWithThreeTenBpLocalDateMappedToDate typeWithLocalDate = new TypeWithThreeTenBpLocalDateMappedToDate();
		typeWithLocalDate.localDate = org.threeten.bp.LocalDate.of(2010, 7, 4);

		Update update = QueryBuilder.update("table");

		mappingCassandraConverter.write(typeWithLocalDate, update);

		assertThat(getAssignmentValues(update)).contains(LocalDate.fromYearMonthDay(2010, 7, 4));
	}

	@Test // DATACASS-206
	public void updateShouldUseSpecifiedColumnNames() {

		UserToken userToken = new UserToken();
		userToken.setUserId(UUID.randomUUID());
		userToken.setToken(UUID.randomUUID());
		userToken.setAdminComment("admin comment");
		userToken.setUserComment("user comment");

		Update update = QueryBuilder.update("table");

		mappingCassandraConverter.write(userToken, update);

		assertThat(getAssignments(update)).containsEntry("admincomment", "admin comment");
		assertThat(getAssignments(update)).containsEntry("user_comment", "user comment");
		assertThat(getWherePredicates(update)).containsEntry("user_id", userToken.getUserId());
	}

	@Test // DATACASS-206
	public void deleteShouldUseSpecifiedColumnNames() {

		UserToken userToken = new UserToken();
		userToken.setUserId(UUID.randomUUID());
		userToken.setToken(UUID.randomUUID());
		userToken.setAdminComment("admin comment");
		userToken.setUserComment("user comment");

		Delete delete = QueryBuilder.delete().from("table");

		mappingCassandraConverter.write(userToken, delete.where());

		assertThat(getWherePredicates(delete)).containsEntry("user_id", userToken.getUserId());
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionUsingPlainId() {

		Delete delete = QueryBuilder.delete().from("table");

		mappingCassandraConverter.write("42", delete.where(), mappingContext.getRequiredPersistentEntity(Person.class));

		assertThat(getWherePredicates(delete)).containsEntry("id", "42");
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionUsingEntity() {

		Delete delete = QueryBuilder.delete().from("table");

		Person person = new Person();
		person.setId("42");

		mappingCassandraConverter.write(person, delete.where(), mappingContext.getRequiredPersistentEntity(Person.class));

		assertThat(getWherePredicates(delete)).containsEntry("id", "42");
	}

	@Test(expected = IllegalArgumentException.class) // DATACASS-308
	public void shouldFailWriteWhereConditionUsingEntityWithNullId() {

		Delete delete = QueryBuilder.delete().from("table");

		mappingCassandraConverter.write(new Person(), delete.where(),
				mappingContext.getRequiredPersistentEntity(Person.class));
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionUsingMapId() {

		Delete delete = QueryBuilder.delete().from("table");

		mappingCassandraConverter.write(id("id", "42"), delete.where(),
				mappingContext.getRequiredPersistentEntity(Person.class));

		assertThat(getWherePredicates(delete)).containsEntry("id", "42");
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionForCompositeKeyUsingEntity() {

		Delete delete = QueryBuilder.delete().from("table");

		TypeWithCompositeKey entity = new TypeWithCompositeKey();
		entity.setFirstname("Walter");
		entity.setLastname("White");

		mappingCassandraConverter.write(entity, delete.where(),
				mappingContext.getRequiredPersistentEntity(TypeWithCompositeKey.class));

		assertThat(getWherePredicates(delete)).containsEntry("firstname", "Walter");
		assertThat(getWherePredicates(delete)).containsEntry("lastname", "White");
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionForCompositeKeyUsingMapId() {

		Delete delete = QueryBuilder.delete().from("table");

		mappingCassandraConverter.write(id("firstname", "Walter").with("lastname", "White"), delete.where(),
				mappingContext.getRequiredPersistentEntity(TypeWithCompositeKey.class));

		assertThat(getWherePredicates(delete)).containsEntry("firstname", "Walter");
		assertThat(getWherePredicates(delete)).containsEntry("lastname", "White");
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionForMapIdKeyUsingEntity() {

		Delete delete = QueryBuilder.delete().from("table");

		TypeWithMapId entity = new TypeWithMapId();
		entity.setFirstname("Walter");
		entity.setLastname("White");

		mappingCassandraConverter.write(entity, delete.where(),
				mappingContext.getRequiredPersistentEntity(TypeWithMapId.class));

		assertThat(getWherePredicates(delete)).containsEntry("firstname", "Walter");
		assertThat(getWherePredicates(delete)).containsEntry("lastname", "White");
	}

	@Test // DATACASS-308
	public void shouldWriteEnumWhereCondition() {

		Delete delete = QueryBuilder.delete().from("table");

		mappingCassandraConverter.write(Condition.MINT, delete.where(),
				mappingContext.getRequiredPersistentEntity(EnumPrimaryKey.class));

		assertThat(getWherePredicates(delete)).containsEntry("condition", "MINT");
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionForMapIdKeyUsingMapId() {

		Delete delete = QueryBuilder.delete().from("table");

		mappingCassandraConverter.write(id("firstname", "Walter").with("lastname", "White"), delete.where(),
				mappingContext.getRequiredPersistentEntity(TypeWithMapId.class));

		assertThat(getWherePredicates(delete)).containsEntry("firstname", "Walter");
		assertThat(getWherePredicates(delete)).containsEntry("lastname", "White");
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionForTypeWithPkClassKeyUsingEntity() {

		Delete delete = QueryBuilder.delete().from("table");

		CompositeKey key = new CompositeKey();
		key.setFirstname("Walter");
		key.setLastname("White");

		TypeWithKeyClass entity = new TypeWithKeyClass();
		entity.setKey(key);

		mappingCassandraConverter.write(entity, delete.where(),
				mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class));

		assertThat(getWherePredicates(delete)).containsEntry("first_name", "Walter");
		assertThat(getWherePredicates(delete)).containsEntry("lastname", "White");
	}

	@Test(expected = IllegalArgumentException.class) // DATACASS-308
	public void shouldFailWritingWhereConditionForTypeWithPkClassKeyUsingEntityWithNullId() {

		Delete delete = QueryBuilder.delete().from("table");

		mappingCassandraConverter.write(new TypeWithKeyClass(), delete.where(),
				mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class));
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionForTypeWithPkClassKeyUsingKey() {

		Delete delete = QueryBuilder.delete().from("table");

		CompositeKey key = new CompositeKey();
		key.setFirstname("Walter");
		key.setLastname("White");

		mappingCassandraConverter.write(key, delete.where(),
				mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class));

		assertThat(getWherePredicates(delete)).containsEntry("first_name", "Walter");
		assertThat(getWherePredicates(delete)).containsEntry("lastname", "White");
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionForTypeWithPkClassKeyUsingMapId() {

		Delete delete = QueryBuilder.delete().from("table");

		mappingCassandraConverter.write(id("firstname", "Walter").with("lastname", "White"), delete.where(),
				mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class));

		assertThat(getWherePredicates(delete)).containsEntry("first_name", "Walter");
		assertThat(getWherePredicates(delete)).containsEntry("lastname", "White");
	}

	@Test(expected = IllegalArgumentException.class) // DATACASS-308
	public void shouldFailWhereConditionForTypeWithPkClassKeyUsingMapIdHavingUnknownProperty() {

		Delete delete = QueryBuilder.delete().from("table");

		mappingCassandraConverter.write(id("unknown", "Walter"), delete.where(),
				mappingContext.getRequiredPersistentEntity(TypeWithMapId.class));
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> getListValue(Insert statement) {

		List<Object> values = getValues(statement);
		return (List<T>) values.stream().filter(value -> value instanceof List).findFirst().orElse(null);
	}

	@SuppressWarnings("unchecked")
	private <T> Set<T> getSetValue(Insert statement) {

		List<Object> values = getValues(statement);
		return (Set<T>) values.stream().filter(value -> value instanceof Set).findFirst().orElse(null);
	}

	@SuppressWarnings("unchecked")
	private List<Object> getValues(Insert statement) {
		return (List<Object>) ReflectionTestUtils.getField(statement, "values");
	}

	@SuppressWarnings("unchecked")
	private Collection<Object> getAssignmentValues(Update statement) {
		return getAssignments(statement).values();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getAssignments(Update statement) {

		Map<String, Object> result = new LinkedHashMap<>();

		Assignments assignments = (Assignments) ReflectionTestUtils.getField(statement, "assignments");

		List<Assignment> listOfAssignments = (List<Assignment>) ReflectionTestUtils.getField(assignments, "assignments");

		for (Assignment assignment : listOfAssignments) {
			result.put(assignment.getColumnName(), ReflectionTestUtils.getField(assignment, "value"));
		}

		return result;
	}

	private Collection<Object> getWhereValues(Update update) {
		return getWherePredicates(update.where()).values();
	}

	private Collection<Object> getWhereValues(BuiltStatement where) {
		return getWherePredicates(where).values();
	}

	private Map<String, Object> getWherePredicates(Update statement) {
		return getWherePredicates(statement.where());
	}

	private Map<String, Object> getWherePredicates(Delete statement) {
		return getWherePredicates(statement.where());
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getWherePredicates(BuiltStatement where) {

		Map<String, Object> result = new LinkedHashMap<>();

		List<Clause> clauses = (List<Clause>) ReflectionTestUtils.getField(where, "clauses");

		for (Clause clause : clauses) {
			result.put(ReflectionTestUtils.invokeMethod(clause, "name"), ReflectionTestUtils.getField(clause, "value"));
		}

		return result;
	}

	@Table
	public static class UnsupportedEnumToOrdinalMapping {

		@PrimaryKey private String id;

		@CassandraType(type = Name.INT) private Condition asOrdinal;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public Condition getAsOrdinal() {
			return asOrdinal;
		}

		public void setAsOrdinal(Condition asOrdinal) {
			this.asOrdinal = asOrdinal;
		}
	}

	@Table
	public static class WithEnumColumns {

		@PrimaryKey private String id;

		private Condition condition;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public Condition getCondition() {
			return condition;
		}

		public void setCondition(Condition condition) {
			this.condition = condition;
		}
	}

	@PrimaryKeyClass
	public static class EnumCompositePrimaryKey implements Serializable {

		@PrimaryKeyColumn(ordinal = 1, type = PrimaryKeyType.PARTITIONED) private Condition condition;

		public EnumCompositePrimaryKey() {}

		public EnumCompositePrimaryKey(Condition condition) {
			this.condition = condition;
		}

		public Condition getCondition() {
			return condition;
		}

		public void setCondition(Condition condition) {
			this.condition = condition;
		}
	}

	@Table
	public static class EnumPrimaryKey {

		@PrimaryKey private Condition condition;

		public Condition getCondition() {
			return condition;
		}

		public void setCondition(Condition condition) {
			this.condition = condition;
		}
	}

	@Table
	public static class CompositeKeyThing {

		@PrimaryKey private EnumCompositePrimaryKey key;

		public CompositeKeyThing() {}

		public CompositeKeyThing(EnumCompositePrimaryKey key) {
			this.key = key;
		}

		public EnumCompositePrimaryKey getKey() {
			return key;
		}

		public void setKey(EnumCompositePrimaryKey key) {
			this.key = key;
		}
	}

	public enum Condition {
		MINT, USED
	}

	@Table
	public static class TypeWithLocalDate {

		@PrimaryKey private String id;

		java.time.LocalDate localDate;
		java.time.LocalDateTime localDateTime;

		List<java.time.LocalDate> list;
		Set<java.time.LocalDate> set;
	}

	/**
	 * Uses Cassandra's {@link Name#DATE} which maps by default to {@link LocalDate}
	 */
	@Table
	@AllArgsConstructor
	public static class TypeWithLocalDateMappedToDate {

		@PrimaryKey private String id;

		@CassandraType(type = Name.DATE) java.time.LocalDate localDate;
	}

	/**
	 * Uses Cassandra's {@link Name#DATE} which maps by default to Joda {@link LocalDate}
	 */
	@Table
	public static class TypeWithJodaLocalDateMappedToDate {

		@PrimaryKey private String id;

		@CassandraType(type = Name.DATE) org.joda.time.LocalDate localDate;
	}

	/**
	 * Uses Cassandra's {@link Name#DATE} which maps by default to Joda {@link LocalDate}
	 */
	@Table
	public static class TypeWithThreeTenBpLocalDateMappedToDate {

		@PrimaryKey private String id;

		@CassandraType(type = Name.DATE) org.threeten.bp.LocalDate localDate;
	}

	@Table
	public static class TypeWithInstant {

		@PrimaryKey private String id;

		Instant instant;
	}

	@Table
	public static class TypeWithZoneId {

		@PrimaryKey private String id;

		ZoneId zoneId;
	}
}
