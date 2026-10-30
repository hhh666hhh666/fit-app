package com.example.fitapp.module.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.agent.entity.AgentToken;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface AgentTokenMapper extends BaseMapper<AgentToken> {

    @Select("SELECT * FROM agent_token WHERE token_hash = #{hash}")
    AgentToken selectByHash(@Param("hash") String hash);

    @Update("UPDATE agent_token SET last_used_at = #{at} WHERE user_id = #{userId}")
    int touch(@Param("userId") Long userId, @Param("at") LocalDateTime at);
}
