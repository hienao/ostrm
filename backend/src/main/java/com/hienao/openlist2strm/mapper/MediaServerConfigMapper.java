package com.hienao.openlist2strm.mapper;

import com.hienao.openlist2strm.entity.MediaServerConfig;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MediaServerConfigMapper {
  MediaServerConfig selectById(@Param("id") Long id);

  MediaServerConfig selectByName(@Param("name") String name);

  List<MediaServerConfig> selectAll();

  List<MediaServerConfig> selectActive();

  int insert(MediaServerConfig config);

  int updateById(MediaServerConfig config);

  int deleteById(@Param("id") Long id);
}
