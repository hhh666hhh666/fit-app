package com.example.fitapp.module.nutrition.entity;

import com.example.fitapp.module.nutrition.service.FoodAliases;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/** {@code food.aliases} 列：库里是 {@code 肥肠|大肠头}，Java 里是字符串列表（ADR 0056）。 */
public class AliasListTypeHandler extends BaseTypeHandler<List<String>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        String text = FoodAliases.join(parameter);
        if (text == null) {
            ps.setNull(i, Types.VARCHAR);
        } else {
            ps.setString(i, text);
        }
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return FoodAliases.split(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return FoodAliases.split(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return FoodAliases.split(cs.getString(columnIndex));
    }
}
