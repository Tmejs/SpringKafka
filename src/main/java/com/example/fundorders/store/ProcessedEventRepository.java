package com.example.fundorders.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.example.fundorders.event.FundOrderStatusEvent;
import com.example.fundorders.event.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedEventRepository {
    private static final String COLUMNS = "schema_version, event_id, order_id, partner_code, "
            + "fund_isin, status, occurred_at, processed_at";
    private static final RowMapper<ProcessedFundOrderEvent> ROW_MAPPER = ProcessedEventRepository::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    public ProcessedEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean insertIfAbsent(FundOrderStatusEvent event, String payloadHash) {
        var params = new MapSqlParameterSource()
                .addValue("eventId", event.eventId())
                .addValue("schemaVersion", event.schemaVersion())
                .addValue("orderId", event.orderId())
                .addValue("partnerCode", event.partnerCode())
                .addValue("fundIsin", event.fundIsin())
                .addValue("status", event.status().name())
                .addValue("occurredAt", Timestamp.from(event.occurredAt()))
                .addValue("payloadHash", payloadHash);
        return jdbc.update("""
                INSERT INTO processed_fund_order_events
                    (event_id, schema_version, order_id, partner_code, fund_isin,
                     status, occurred_at, payload_hash)
                VALUES (:eventId, :schemaVersion, :orderId, :partnerCode, :fundIsin,
                        :status, :occurredAt, :payloadHash)
                ON CONFLICT (event_id) DO NOTHING
                """, params) == 1;
    }

    public Optional<String> findPayloadHash(UUID eventId) {
        return jdbc.query("SELECT payload_hash FROM processed_fund_order_events WHERE event_id = :eventId",
                Map.of("eventId", eventId), (rs, rowNum) -> rs.getString(1)).stream().findFirst();
    }

    public Optional<ProcessedFundOrderEvent> findByEventId(UUID eventId) {
        return jdbc.query("SELECT " + COLUMNS
                        + " FROM processed_fund_order_events WHERE event_id = :eventId",
                Map.of("eventId", eventId), ROW_MAPPER).stream().findFirst();
    }

    public List<ProcessedFundOrderEvent> findPage(Pageable page) {
        return jdbc.query("SELECT " + COLUMNS + " FROM processed_fund_order_events "
                        + "ORDER BY occurred_at DESC, event_id DESC LIMIT :limit OFFSET :offset",
                Map.of("limit", page.getPageSize(), "offset", page.getOffset()), ROW_MAPPER);
    }

    public long count() {
        var value = jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM processed_fund_order_events", Long.class);
        return value == null ? 0 : value;
    }

    private static ProcessedFundOrderEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ProcessedFundOrderEvent(
                rs.getInt("schema_version"),
                rs.getObject("event_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("partner_code"),
                rs.getString("fund_isin"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getTimestamp("processed_at").toInstant());
    }
}
