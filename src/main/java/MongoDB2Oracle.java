import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.types.Binary;

public class MongoDB2Oracle {
  public static void main(String[] args) {
    String oracleUrl = "jdbc:oracle:thin:@127.0.0.1:1521/orcl";
    String oracleUserName = "sys";
    String oraclePassword = "sys";

    String mongoDBUrl = "mongodb://127.0.0.1:27017";
    String mongoDBUrlDatabase = "sys";

    MongoClient mongoClient = MongoClients.create(mongoDBUrl);
    MongoDatabase database = mongoClient.getDatabase(mongoDBUrlDatabase);

    HashMap<String, List<String>> tables = readTableStructure("src/main/java/migration.info");

    try (Connection conn = DriverManager.getConnection(oracleUrl, oracleUserName, oraclePassword)) {
      conn.setAutoCommit(false);
      for (String table_name : tables.keySet()) {
        List<String> table_fields = tables.get(table_name);
        String insertSQL = generateInsertSQL(table_name, table_fields);
        String selectSQL = generateSelectSQL(table_name, table_fields);
        ResultSet resultSet = conn.createStatement()
            .executeQuery(selectSQL);
        ResultSetMetaData metaData = resultSet.getMetaData();
        try (PreparedStatement preparedStatement = conn.prepareStatement(insertSQL)) {
          MongoCollection<Document> collection = database.getCollection(table_name);
          FindIterable<Document> documents = collection.find(Filters.empty());
          for (Document doc : documents) {
            for (int i = 0; i < preparedStatement.getParameterMetaData().getParameterCount();
                 i++) {
              String columnName = table_fields.get(i);
              setPreparedStatementValue(preparedStatement, i + 1, doc, columnName,
                  metaData.getColumnType(i + 1));
            }
            preparedStatement.addBatch();
          }
          preparedStatement.executeBatch();
          conn.commit();
          System.out.println("Table " + table_name + " migration completed successfully!");
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    } finally {
      mongoClient.close();
    }
  }

  public static String generateInsertSQL(String tableName, List<String> columnNames) {
    String columns = String.join(", ", columnNames);
    String placeholders = columnNames.stream()
        .map(col -> "?")
        .collect(Collectors.joining(", "));
    String insertSQL =
        "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")";
    System.out.println("Insert SQL: " + insertSQL);
    return insertSQL;
  }


  public static String generateSelectSQL(String tableName, List<String> columnNames) {
    String columns = String.join(", ", columnNames);
    String insertSQL =
        "SELECT " + columns + " FROM " + tableName + " WHERE 1=0";
    System.out.println("Select SQL: " + insertSQL);
    return insertSQL;
  }

  private static void setPreparedStatementValue(PreparedStatement preparedStatement,
                                                int parameterIndex, Document doc, String columnName,
                                                int sqlType)
      throws SQLException {
    Object value = doc.get(columnName); // 获取MongoDB文档中的值
    if (value != null) {
      try {
        switch (sqlType) {
          case Types.VARCHAR:
          case Types.CHAR:
          case Types.CLOB:
            preparedStatement.setString(parameterIndex, doc.get(columnName).toString());
            break;
          case Types.INTEGER:
          case Types.BIGINT:
          case Types.SMALLINT:
          case Types.TINYINT:
            preparedStatement.setInt(parameterIndex, doc.getInteger(columnName));
            break;
          case Types.DOUBLE:
          case Types.FLOAT:
          case Types.REAL:
          case Types.DECIMAL:
          case Types.NUMERIC:
            preparedStatement
                .setDouble(parameterIndex, Double.valueOf(doc.get(columnName).toString()));
            break;
          case Types.BOOLEAN:
          case Types.BIT:
            preparedStatement.setBoolean(parameterIndex, doc.getBoolean(columnName));
            break;
          case Types.DATE:
            preparedStatement
                .setDate(parameterIndex, new java.sql.Date(doc.getDate(columnName).getTime()));
            break;
          case Types.TIMESTAMP:
            preparedStatement
                .setTimestamp(parameterIndex,
                    new java.sql.Timestamp(
                        TimeUnit.SECONDS
                            .toMillis(doc.get(columnName, BsonTimestamp.class).getTime())));
            break;
          case Types.BLOB:
            preparedStatement
                .setBytes(parameterIndex, Base64
                    .getEncoder().encodeToString(doc.get(columnName, Binary.class).getData())
                    .getBytes(
                        StandardCharsets.UTF_8));
            break;
          default:
            break;
        }
      } catch (NumberFormatException e) {
        preparedStatement.setString(parameterIndex, value.toString());
      }
    } else {
      preparedStatement.setNull(parameterIndex, Types.NULL);
    }
  }

  public static HashMap<String, List<String>> readTableStructure(String filePath) {
    HashMap<String, List<String>> tables = new HashMap<>();

    try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (line.trim().isEmpty() || line.startsWith("#")) {
          continue;
        }

        // 使用逗号分隔每一行的数据
        String[] parts = line.split(",");

        // 第一个字符串作为key
        String table_name = parts[0].trim();

        // 剩余的字符串作为value列表的元素
        List<String> fields = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
          fields.add(parts[i].trim());
        }

        // 将数据放入Map中
        tables.put(table_name, fields);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    // 打印Map内容以验证结果
    for (Map.Entry<String, List<String>> entry : tables.entrySet()) {
      System.out.println("Table: " + entry.getKey() + ", Fields: " + entry.getValue());
    }
    return tables;
  }
}