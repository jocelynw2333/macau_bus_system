package com.albez.bus.mapper;

import com.albez.bus.model.CollectionSnapshot;
import com.albez.bus.model.SnapshotStation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CollectionSnapshotMapper extends BaseMapper<CollectionSnapshot> {
    @Select("""
            SELECT COUNT(*) FROM collection_snapshot
            WHERE route_name = #{routeName} AND direction = #{direction}
            """)
    long countByRoute(@Param("routeName") String routeName, @Param("direction") Integer direction);

    @Select("""
            SELECT id, collected_at, route_name, direction, station_count, bus_count
            FROM collection_snapshot
            WHERE route_name = #{routeName} AND direction = #{direction}
            ORDER BY collected_at DESC, id DESC LIMIT 1
            """)
    CollectionSnapshot selectLatestByRoute(@Param("routeName") String routeName,
                                           @Param("direction") Integer direction);

    @Select("""
            SELECT jt.sta_code AS code, COALESCE(si.sta_zh_tw, si.sta_zh_cn, jt.sta_code) AS name,
                   jt.sta_index AS `index`
            FROM collection_snapshot s
            JOIN JSON_TABLE(s.raw_json, '$.response.data.routeInfo[*]'
              COLUMNS (sta_index FOR ORDINALITY, sta_code VARCHAR(20) PATH '$.staCode')) jt
            LEFT JOIN station_info si ON si.sta_code = jt.sta_code
            WHERE s.id = #{snapshotId} ORDER BY jt.sta_index
            """)
    List<SnapshotStation> selectStationsBySnapshotId(@Param("snapshotId") Long snapshotId);
}
