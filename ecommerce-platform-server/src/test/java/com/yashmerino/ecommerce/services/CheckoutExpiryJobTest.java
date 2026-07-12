package com.yashmerino.ecommerce.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutExpiryJobTest {

    @Mock private JdbcTemplate jdbc;

    private CheckoutExpiryJob job;

    @BeforeEach
    void setUp() {
        job = new CheckoutExpiryJob(jdbc);
    }

    @Test
    void expireAwaitingCheckouts_releasesInventory() {
        when(jdbc.query(contains("FOR UPDATE SKIP LOCKED"), any(RowMapper.class)))
                .thenReturn(List.of(1L));

        when(jdbc.update(contains("UPDATE payments"), anyLong())).thenReturn(1);

        doAnswer(inv -> {
            ResultSetExtractor<?> extractor = inv.getArgument(1);
            var rs = mock(java.sql.ResultSet.class);
            when(rs.next()).thenReturn(true, false);
            when(rs.getLong(1)).thenReturn(1L);
            when(rs.getInt(2)).thenReturn(3);
            extractor.extractData(rs);
            return null;
        }).when(jdbc).query(contains("inventory_reservations"), any(ResultSetExtractor.class), anyLong());

        when(jdbc.update(contains("UPDATE inventory_reservations SET status='EXPIRED'"), anyLong())).thenReturn(1);

        doAnswer(inv -> null).when(jdbc).query(contains("promotion_reservations"), any(ResultSetExtractor.class), anyLong());

        when(jdbc.query(contains("point_reservations"), any(ResultSetExtractor.class), anyLong())).thenReturn(null);

        when(jdbc.update(contains("UPDATE orders SET status='EXPIRED'"), anyLong())).thenReturn(1);

        job.expireAwaitingCheckouts();

        verify(jdbc).update(contains("UPDATE products"), anyInt(), anyLong(), anyInt());
        verify(jdbc).update(contains("UPDATE inventory_reservations SET status='EXPIRED'"), anyLong());
        verify(jdbc).update(contains("UPDATE orders SET status='EXPIRED'"), anyLong());
    }

    @Test
    void expireAwaitingCheckouts_releasesPromotion() {
        when(jdbc.query(contains("FOR UPDATE SKIP LOCKED"), any(RowMapper.class)))
                .thenReturn(List.of(1L));

        when(jdbc.update(contains("UPDATE payments"), anyLong())).thenReturn(1);

        doAnswer(inv -> null).when(jdbc).query(contains("inventory_reservations"), any(ResultSetExtractor.class), anyLong());
        when(jdbc.update(contains("UPDATE inventory_reservations SET status='EXPIRED'"), anyLong())).thenReturn(1);

        doAnswer(inv -> {
            ResultSetExtractor<?> extractor = inv.getArgument(1);
            var rs = mock(java.sql.ResultSet.class);
            when(rs.next()).thenReturn(true, false);
            when(rs.getLong(1)).thenReturn(10L);
            when(rs.getLong(2)).thenReturn(5L);
            when(rs.getLong(3)).thenReturn(1L);
            extractor.extractData(rs);
            return null;
        }).when(jdbc).query(contains("promotion_reservations"), any(ResultSetExtractor.class), anyLong());

        when(jdbc.query(contains("point_reservations"), any(ResultSetExtractor.class), anyLong())).thenReturn(null);

        when(jdbc.update(contains("UPDATE orders SET status='EXPIRED'"), anyLong())).thenReturn(1);

        job.expireAwaitingCheckouts();

        verify(jdbc).update(contains("UPDATE promotions SET remaining_usage"), anyLong());
        verify(jdbc).update(contains("UPDATE promotion_usage_counters"), anyLong(), anyLong());
        verify(jdbc).update(contains("UPDATE promotion_reservations SET status='EXPIRED'"), anyLong());
    }

    @Test
    void expireAwaitingCheckouts_releasesPoints() {
        when(jdbc.query(contains("FOR UPDATE SKIP LOCKED"), any(RowMapper.class)))
                .thenReturn(List.of(1L));

        when(jdbc.update(contains("UPDATE payments"), anyLong())).thenReturn(1);

        doAnswer(inv -> null).when(jdbc).query(contains("inventory_reservations"), any(ResultSetExtractor.class), anyLong());
        when(jdbc.update(contains("UPDATE inventory_reservations SET status='EXPIRED'"), anyLong())).thenReturn(1);

        doAnswer(inv -> null).when(jdbc).query(contains("promotion_reservations"), any(ResultSetExtractor.class), anyLong());

        doAnswer(inv -> {
            ResultSetExtractor<?> extractor = inv.getArgument(1);
            var rs = mock(java.sql.ResultSet.class);
            when(rs.next()).thenReturn(true, false);
            when(rs.getLong("id")).thenReturn(20L);
            when(rs.getLong("account_id")).thenReturn(1L);
            when(rs.getInt("total_points")).thenReturn(50);
            when(rs.getString("currency")).thenReturn("EUR");
            return extractor.extractData(rs);
        }).when(jdbc).query(contains("point_reservations"), any(ResultSetExtractor.class), anyLong());

        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyLong())).thenReturn(30);
        when(jdbc.update(contains("UPDATE point_lots"), anyLong())).thenReturn(1);

        doAnswer(inv -> null).when(jdbc).query(contains("point_reservation_allocations"), any(ResultSetExtractor.class), anyLong());

        when(jdbc.update(contains("UPDATE point_reservations SET status="), anyString(), anyLong())).thenReturn(1);
        when(jdbc.update(contains("UPDATE orders SET status='EXPIRED'"), anyLong())).thenReturn(1);

        job.expireAwaitingCheckouts();

        verify(jdbc).update(contains("UPDATE loyalty_accounts SET available_points"), anyInt(), anyInt(), anyLong(), anyInt());
        verify(jdbc).update(contains("UPDATE point_reservations SET status="), anyString(), anyLong());
    }

    @Test
    void expireAwaitingCheckouts_doesNotExpireAlreadyPending() {
        when(jdbc.query(contains("FOR UPDATE SKIP LOCKED"), any(RowMapper.class)))
                .thenReturn(List.of(1L));

        when(jdbc.update(contains("UPDATE payments"), anyLong())).thenReturn(0);

        job.expireAwaitingCheckouts();

        verify(jdbc, never()).update(contains("UPDATE products"), any(), any(), any());
        verify(jdbc, never()).update(contains("UPDATE orders SET status='EXPIRED'"), anyLong());
    }

    @Test
    void expireAwaitingCheckouts_skipLocked_doesNotDoubleProcess() {
        when(jdbc.query(contains("FOR UPDATE SKIP LOCKED"), any(RowMapper.class)))
                .thenReturn(List.of(1L, 2L));

        when(jdbc.update(contains("UPDATE payments"), anyLong())).thenReturn(1);
        doAnswer(inv -> null).when(jdbc).query(contains("inventory_reservations"), any(ResultSetExtractor.class), anyLong());
        when(jdbc.update(contains("UPDATE inventory_reservations SET status='EXPIRED'"), anyLong())).thenReturn(1);

        doAnswer(inv -> null).when(jdbc).query(contains("promotion_reservations"), any(ResultSetExtractor.class), anyLong());
        when(jdbc.query(contains("point_reservations"), any(ResultSetExtractor.class), anyLong())).thenReturn(null);
        when(jdbc.update(contains("UPDATE orders SET status='EXPIRED'"), anyLong())).thenReturn(1);

        job.expireAwaitingCheckouts();

        verify(jdbc, times(2)).update(contains("UPDATE payments"), anyLong());
        verify(jdbc, times(2)).update(contains("UPDATE orders SET status='EXPIRED'"), anyLong());
    }
}
