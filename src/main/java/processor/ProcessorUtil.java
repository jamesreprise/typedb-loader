package processor;

import configuration.DataConfigEntry;
import configuration.ProcessorConfigEntry;
import graql.lang.pattern.variable.ThingVariable.Attribute;
import graql.lang.pattern.variable.ThingVariable.Relation;
import graql.lang.pattern.variable.ThingVariable.Thing;
import graql.lang.pattern.variable.UnboundVariable;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import preprocessor.RegexPreprocessor;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;

public class ProcessorUtil {

    private static final Logger dataLogger = LogManager.getLogger("com.bayer.dt.grami.data");
    private static final Logger appLogger = LogManager.getLogger("com.bayer.dt.grami");

    public static String[] tokenizeCSVStandard(String row, char fileSeparator) {
        if (row != null && !row.isEmpty()) {
            try {
                return parseCSVString(row, fileSeparator);
            } catch (IOException ioe) {
                dataLogger.warn("row: <" + row + "> does not conform to RFC4180 - escaping all quotes and trying to insert again" + ioe);
                String newRow = row.replace("\"", "\\\"");
                try {
                    return parseCSVString(newRow, fileSeparator);
                } catch (IOException ioe2) {
                    dataLogger.error("CANNOT INSERT ROW - DOES NOT CONFORM TO RFC4180 and removing quotes didn't fix the issue..." + ioe2);
                }
            }
        }
        return new String[]{""};
    }

    private static String[] parseCSVString(String string, char fileSeparator) throws IOException {
        ArrayList<CSVRecord> returnList = (ArrayList<CSVRecord>) CSVParser.parse(string, CSVFormat.RFC4180.withDelimiter(fileSeparator)).getRecords();
        String[] ret = new String[returnList.get(0).size()];
        for (int i = 0; i < returnList.get(0).size(); i++) {
            ret[i] = returnList.get(0).get(i);
        }
        return ret;
    }

    public static String cleanToken(String token) {
        //TODO - expand cleaning of other strange characters at some point
        String cleaned = token.replace("\"", "");
        cleaned = cleaned.replace("\\", "");
        cleaned = cleaned.trim();
        return cleaned;
    }

    public static void malformedRow(String row,
                                    String[] rowTokens,
                                    int numberOfColumns) throws Exception {
        if (rowTokens.length > numberOfColumns) {
            throw new Exception("malformed input row (additional separator characters found) not inserted - fix the following and restart migration: " + row);
        }
    }

    public static int idxOf(String[] headerTokens,
                            String columnName) {
        return Arrays.asList(headerTokens).indexOf(columnName);
    }

    public static int[] indicesOf(String[] headerTokens,
                                  String[] columnNames) {
        int[] indices = new int[columnNames.length];
        int i = 0;
        for (String columnName : columnNames) {
            indices[i] = Arrays.asList(headerTokens).indexOf(columnName);
            i++;
        }
        return indices;
    }

    public static Attribute addValue(String[] tokens,
                                     UnboundVariable statement,
                                     int lineNumber,
                                     String[] columnNames,
                                     DataConfigEntry.DataConfigGeneratorMapping generatorMappingForAttribute,
                                     ProcessorConfigEntry pce,
                                     DataConfigEntry.DataConfigGeneratorMapping.PreprocessorConfig preprocessorConfig) {
        String attributeGeneratorKey = generatorMappingForAttribute.getGenerator();
        ProcessorConfigEntry.ConceptGenerator attributeGenerator = pce.getAttributeGenerator(attributeGeneratorKey);
        String columnName = generatorMappingForAttribute.getColumnName();
        int columnNameIndex = idxOf(columnNames, columnName);
        Attribute att = null;

        if (columnNameIndex == -1) {
            dataLogger.error("Column name: <" + columnName + "> was not found in file being processed");
        } else {
            if (columnNameIndex < tokens.length &&
                    tokens[columnNameIndex] != null &&
                    !cleanToken(tokens[columnNameIndex]).isEmpty()) {
                AttributeValueType attributeValueType = attributeGenerator.getValueType();
                String cleanedToken = cleanToken(tokens[columnNameIndex]);
                att = addAttributeValueOfType(statement, attributeValueType, cleanedToken, lineNumber, preprocessorConfig);
            }
        }
        return att;
    }

    public static Thing addAttribute(String[] tokens,
                                     Thing statement,
                                     String[] columnNames,
                                     int lineNumber,
                                     DataConfigEntry.DataConfigGeneratorMapping generatorMappingForAttribute,
                                     ProcessorConfigEntry pce,
                                     DataConfigEntry.DataConfigGeneratorMapping.PreprocessorConfig preprocessorConfig) {
        String attributeGeneratorKey = generatorMappingForAttribute.getGenerator();
        ProcessorConfigEntry.ConceptGenerator attributeGenerator = pce.getAttributeGenerator(attributeGeneratorKey);
        String columnListSeparator = generatorMappingForAttribute.getListSeparator();
        String columnName = generatorMappingForAttribute.getColumnName();
        int columnNameIndex = idxOf(columnNames, columnName);

        if (columnNameIndex == -1) {
            dataLogger.error("Column name: <" + columnName + "> was not found in file being processed");
        } else {
            if (columnNameIndex < tokens.length &&
                    tokens[columnNameIndex] != null &&
                    !cleanToken(tokens[columnNameIndex]).isEmpty()) {
                String attributeType = attributeGenerator.getAttributeType();
                AttributeValueType attributeValueType = attributeGenerator.getValueType();
                String cleanedToken = cleanToken(tokens[columnNameIndex]);
                statement = cleanExplodeAdd(statement, cleanedToken, attributeType, attributeValueType, lineNumber, columnListSeparator, preprocessorConfig);
            }
        }
        return statement;
    }

    public static Relation addAttribute(String[] tokens,
                                        Relation statement,
                                        String[] columnNames,
                                        int lineNumber,
                                        DataConfigEntry.DataConfigGeneratorMapping generatorMappingForAttribute,
                                        ProcessorConfigEntry pce,
                                        DataConfigEntry.DataConfigGeneratorMapping.PreprocessorConfig preprocessorConfig) {
        String attributeGeneratorKey = generatorMappingForAttribute.getGenerator();
        ProcessorConfigEntry.ConceptGenerator attributeGenerator = pce.getAttributeGenerator(attributeGeneratorKey);
        String columnListSeparator = generatorMappingForAttribute.getListSeparator();
        String columnName = generatorMappingForAttribute.getColumnName();
        int columnNameIndex = idxOf(columnNames, columnName);

        if (columnNameIndex == -1) {
            dataLogger.error("Column name: <" + columnName + "> was not found in file being processed");
        } else {
            if (columnNameIndex < tokens.length &&
                    tokens[columnNameIndex] != null &&
                    !cleanToken(tokens[columnNameIndex]).isEmpty()) {
                String attributeType = attributeGenerator.getAttributeType();
                AttributeValueType attributeValueType = attributeGenerator.getValueType();
                String cleanedToken = cleanToken(tokens[columnNameIndex]);
                statement = cleanExplodeAdd(statement, cleanedToken, attributeType, attributeValueType, columnListSeparator, lineNumber, preprocessorConfig);
            }
        }
        return statement;
    }

    public static Thing addAttribute(String[] tokens,
                                     UnboundVariable statement,
                                     int lineNumber,
                                     String[] columnNames,
                                     DataConfigEntry.DataConfigGeneratorMapping generatorMappingForAttribute,
                                     ProcessorConfigEntry pce,
                                     DataConfigEntry.DataConfigGeneratorMapping.PreprocessorConfig preprocessorConfig) {
        String attributeGeneratorKey = generatorMappingForAttribute.getGenerator();
        ProcessorConfigEntry.ConceptGenerator attributeGenerator = pce.getAttributeGenerator(attributeGeneratorKey);
        String columnListSeparator = generatorMappingForAttribute.getListSeparator();
        String columnName = generatorMappingForAttribute.getColumnName();
        int columnNameIndex = idxOf(columnNames, columnName);
        Thing returnThing = null;

        if (columnNameIndex == -1) {
            dataLogger.error("Column name: <" + columnName + "> was not found in file being processed");
        } else {
            if (columnNameIndex < tokens.length &&
                    tokens[columnNameIndex] != null &&
                    !cleanToken(tokens[columnNameIndex]).isEmpty()) {
                String attributeType = attributeGenerator.getAttributeType();
                AttributeValueType attributeValueType = attributeGenerator.getValueType();
                String cleanedToken = cleanToken(tokens[columnNameIndex]);
                returnThing = cleanExplodeAdd(statement, cleanedToken, attributeType, attributeValueType, columnListSeparator, lineNumber, preprocessorConfig);
            }
        }
        return returnThing;
    }

    public static Thing cleanExplodeAdd(Thing statement,
                                        String cleanedToken,
                                        String conceptType,
                                        AttributeValueType valueType,
                                        int lineNumber,
                                        String listSeparator,
                                        DataConfigEntry.DataConfigGeneratorMapping.PreprocessorConfig preprocessorConfig) {
        if (listSeparator != null) {
            for (String exploded : cleanedToken.split(listSeparator)) {
                String cleanedExplodedToken = cleanToken(exploded);
                if (!cleanedExplodedToken.isEmpty()) {
                    statement = addAttributeOfColumnType(statement, conceptType, valueType, cleanedExplodedToken, lineNumber, preprocessorConfig);
                }
            }
            return statement;
        } else {
            return addAttributeOfColumnType(statement, conceptType, valueType, cleanedToken, lineNumber, preprocessorConfig);
        }
    }

    public static Relation cleanExplodeAdd(Relation statement,
                                           String cleanedToken,
                                           String conceptType,
                                           AttributeValueType valueType,
                                           String listSeparator,
                                           int lineNumber,
                                           DataConfigEntry.DataConfigGeneratorMapping.PreprocessorConfig preprocessorConfig) {
        if (listSeparator != null) {
            for (String exploded : cleanedToken.split(listSeparator)) {
                String cleanedExplodedToken = cleanToken(exploded);
                if (!cleanedExplodedToken.isEmpty()) {
                    statement = addAttributeOfColumnType(statement, conceptType, valueType, cleanedExplodedToken, lineNumber, preprocessorConfig);
                }
            }
            return statement;
        } else {
            return addAttributeOfColumnType(statement, conceptType, valueType, cleanedToken, lineNumber, preprocessorConfig);
        }
    }

    public static Thing cleanExplodeAdd(UnboundVariable statement,
                                        String cleanedToken,
                                        String conceptType,
                                        AttributeValueType valueType,
                                        String listSeparator,
                                        int lineNumber,
                                        DataConfigEntry.DataConfigGeneratorMapping.PreprocessorConfig preprocessorConfig) {
        Thing returnThing = null;
        if (listSeparator != null) {
            int count = 0;
            for (String exploded : cleanedToken.split(listSeparator)) {
                String cleanedExplodedToken = cleanToken(exploded);
                if (!cleanedExplodedToken.isEmpty()) {
                    if (count == 0) {
                        returnThing = addAttributeOfColumnType(statement, conceptType, valueType, cleanedExplodedToken, lineNumber, preprocessorConfig);
                    } else {
                        returnThing = addAttributeOfColumnType(returnThing, conceptType, valueType, cleanedExplodedToken, lineNumber, preprocessorConfig);
                    }
                    count++;
                }
            }
            return returnThing;
        } else {
            return addAttributeOfColumnType(statement, conceptType, valueType, cleanedToken, lineNumber, preprocessorConfig);
        }
    }

    public static Thing addAttributeOfColumnType(Thing statement,
                                                 String conceptType,
                                                 AttributeValueType valueType,
                                                 String cleanedValue,
                                                 int lineNumber,
                                                 DataConfigEntry.DataConfigGeneratorMapping.PreprocessorConfig preprocessorConfig) {
        if (preprocessorConfig != null) {
            cleanedValue = applyPreprocessor(cleanedValue, preprocessorConfig);
        }

        switch (valueType) {
            case STRING:
                statement = statement.has(conceptType, cleanedValue);
                break;
            case LONG:
                try {
                    statement = statement.has(conceptType, Integer.parseInt(cleanedValue));
                } catch (NumberFormatException numberFormatException) {
                    dataLogger.warn(String.format("row < %s > has column of type <long> for variable < %s > with non-<long> value - skipping column - < %s >", lineNumber, conceptType, numberFormatException.getMessage()));
                }
                break;
            case DOUBLE:
                try {
                    statement = statement.has(conceptType, Double.parseDouble(cleanedValue));
                } catch (NumberFormatException numberFormatException) {
                    dataLogger.warn(String.format("row < %s > has column of type <double> for variable < %s > with non-<double> value - skipping column - < %s >", lineNumber, conceptType, numberFormatException.getMessage()));
                }
                break;
            case BOOLEAN:
                if (cleanedValue.equalsIgnoreCase("true")) {
                    statement = statement.has(conceptType, true);
                } else if (cleanedValue.equalsIgnoreCase("false")) {
                    statement = statement.has(conceptType, false);
                } else {
                    dataLogger.warn(String.format("row < %s > has column of type <boolean> for variable < %s > with non-<boolean> value - skipping column", lineNumber, conceptType));
                }
                break;
            case DATETIME:
                try {
                    DateTimeFormatter isoDateFormatter = DateTimeFormatter.ISO_DATE;
                    String[] dt = cleanedValue.split("T");
                    LocalDate date = LocalDate.parse(dt[0], isoDateFormatter);
                    if (dt.length > 1) {
                        LocalTime time = LocalTime.parse(dt[1], DateTimeFormatter.ISO_TIME);
                        LocalDateTime dateTime = date.atTime(time);
                        statement = statement.has(conceptType, dateTime);
                    } else {
                        LocalDateTime dateTime = date.atStartOfDay();
                        statement = statement.has(conceptType, dateTime);
                    }
                } catch (DateTimeException dateTimeException) {
                    dataLogger.warn(String.format("row < %s > has column of type <datetime> for variable < %s > with non-<ISO 8601 format> datetime value:  - < %s >", lineNumber, conceptType, dateTimeException.getMessage()));
                }
                break;
            default:
                dataLogger.warn("row < %s > column type not valid - must be either: string, long, double, boolean, or datetime");
                break;
        }
        return statement;
    }


    public static Relation addAttributeOfColumnType(Relation statement,
                                                    String conceptType,
                                                    AttributeValueType valueType,
                                                    String cleanedValue,
                                                    int lineNumber,
                                                    DataConfigEntry.DataConfigGeneratorMapping.PreprocessorConfig preprocessorConfig) {
        if (preprocessorConfig != null) {
            cleanedValue = applyPreprocessor(cleanedValue, preprocessorConfig);
        }

        switch (valueType) {
            case STRING:
                statement = statement.has(conceptType, cleanedValue);
                break;
            case LONG:
                try {
                    statement = statement.has(conceptType, Integer.parseInt(cleanedValue));
                } catch (NumberFormatException numberFormatException) {
                    dataLogger.warn(String.format("row < %s > has column of type <long> for variable < %s > with non-<long> value - skipping column - < %s >", lineNumber, conceptType, numberFormatException.getMessage()));
                }
                break;
            case DOUBLE:
                try {
                    statement = statement.has(conceptType, Double.parseDouble(cleanedValue));
                } catch (NumberFormatException numberFormatException) {
                    dataLogger.warn(String.format("row < %s > has column of type <double> for variable < %s > with non-<double> value - skipping column - < %s >", lineNumber, conceptType, numberFormatException.getMessage()));
                }
                break;
            case BOOLEAN:
                if (cleanedValue.equalsIgnoreCase("true")) {
                    statement = statement.has(conceptType, true);
                } else if (cleanedValue.equalsIgnoreCase("false")) {
                    statement = statement.has(conceptType, false);
                } else {
                    dataLogger.warn(String.format("row < %s > has column of type <boolean> for variable < %s > with non-<boolean> value - skipping column", lineNumber, conceptType));
                }
                break;
            case DATETIME:
                try {
                    DateTimeFormatter isoDateFormatter = DateTimeFormatter.ISO_DATE;
                    String[] dt = cleanedValue.split("T");
                    LocalDate date = LocalDate.parse(dt[0], isoDateFormatter);
                    if (dt.length > 1) {
                        LocalTime time = LocalTime.parse(dt[1], DateTimeFormatter.ISO_TIME);
                        LocalDateTime dateTime = date.atTime(time);
                        statement = statement.has(conceptType, dateTime);
                    } else {
                        LocalDateTime dateTime = date.atStartOfDay();
                        statement = statement.has(conceptType, dateTime);
                    }
                } catch (DateTimeException dateTimeException) {
                    dataLogger.warn(String.format("row < %s > has column of type <datetime> for variable < %s > with non-<ISO 8601 format> datetime value:  - < %s >", lineNumber, conceptType, dateTimeException.getMessage()));
                }
                break;
            default:
                dataLogger.warn(String.format("row < %s > column type not valid - must be either: string, long, double, boolean, or datetime", lineNumber));
                break;
        }
        return statement;
    }

    public static Thing addAttributeOfColumnType(UnboundVariable statement,
                                                 String conceptType,
                                                 AttributeValueType valueType,
                                                 String cleanedValue,
                                                 int lineNumber,
                                                 DataConfigEntry.DataConfigGeneratorMapping.PreprocessorConfig preprocessorConfig) {
        if (preprocessorConfig != null) {
            cleanedValue = applyPreprocessor(cleanedValue, preprocessorConfig);
            appLogger.debug("processor processed cleaned value: " + cleanedValue);
        }
        Thing returnThing = null;

        switch (valueType) {
            case STRING:
                returnThing = statement.has(conceptType, cleanedValue);
                break;
            case LONG:
                try {
                    returnThing = statement.has(conceptType, Integer.parseInt(cleanedValue));
                } catch (NumberFormatException numberFormatException) {
                    dataLogger.warn(String.format("row < %s > has column of type <long> for variable < %s > with non-<long> value - skipping column - < %s >", lineNumber, conceptType, numberFormatException.getMessage()));
                }
                break;
            case DOUBLE:
                try {
                    returnThing = statement.has(conceptType, Double.parseDouble(cleanedValue));
                } catch (NumberFormatException numberFormatException) {
                    dataLogger.warn(String.format("row < %s > has column of type <double> for variable < %s > with non-<double> value - skipping column - < %s >", lineNumber, conceptType, numberFormatException.getMessage()));
                }
                break;
            case BOOLEAN:
                if (cleanedValue.equalsIgnoreCase("true")) {
                    returnThing = statement.has(conceptType, true);
                } else if (cleanedValue.equalsIgnoreCase("false")) {
                    returnThing = statement.has(conceptType, false);
                } else {
                    dataLogger.warn(String.format("row < %s > has column of type <boolean> for variable < %s > with non-<boolean> value - skipping column", lineNumber, conceptType));
                }
                break;
            case DATETIME:
                try {
                    DateTimeFormatter isoDateFormatter = DateTimeFormatter.ISO_DATE;
                    String[] dt = cleanedValue.split("T");
                    LocalDate date = LocalDate.parse(dt[0], isoDateFormatter);
                    if (dt.length > 1) {
                        LocalTime time = LocalTime.parse(dt[1], DateTimeFormatter.ISO_TIME);
                        LocalDateTime dateTime = date.atTime(time);
                        returnThing = statement.has(conceptType, dateTime);
                    } else {
                        LocalDateTime dateTime = date.atStartOfDay();
                        returnThing = statement.has(conceptType, dateTime);
                    }
                } catch (DateTimeException dateTimeException) {
                    dataLogger.warn(String.format("row < %s > has column of type <datetime> for variable < %s > with non-<ISO 8601 format> datetime value:  - < %s >", lineNumber, conceptType, dateTimeException.getMessage()));
                }
                break;
            default:
                dataLogger.warn(String.format("row < %s > column type not valid - must be either: string, long, double, boolean, or datetime", lineNumber));
                break;
        }
        return returnThing;
    }

    public static Attribute addAttributeValueOfType(UnboundVariable unboundVar,
                                                    AttributeValueType valueType,
                                                    String cleanedValue,
                                                    int lineNumber,
                                                    DataConfigEntry.DataConfigGeneratorMapping.PreprocessorConfig preprocessorConfig) {
        if (preprocessorConfig != null) {
            cleanedValue = applyPreprocessor(cleanedValue, preprocessorConfig);
        }
        Attribute att = null;

        switch (valueType) {
            case STRING:
                att = unboundVar.eq(cleanedValue);
                break;
            case LONG:
                try {
                    att = unboundVar.eq(Integer.parseInt(cleanedValue));
                } catch (NumberFormatException numberFormatException) {
                    dataLogger.warn(String.format("row < %s > has column of type <long> with non-<long> value - skipping column - < %s >", lineNumber, numberFormatException.getMessage()));
                }
                break;
            case DOUBLE:
                try {
                    att = unboundVar.eq(Double.parseDouble(cleanedValue));
                } catch (NumberFormatException numberFormatException) {
                    dataLogger.warn(String.format("row < %s > has column of type <double> with non-<double> value - skipping column - < %s >", lineNumber, numberFormatException.getMessage()));
                }
                break;
            case BOOLEAN:
                if (cleanedValue.equalsIgnoreCase("true")) {
                    att = unboundVar.eq(true);
                } else if (cleanedValue.equalsIgnoreCase("false")) {
                    att = unboundVar.eq(false);
                } else {
                    dataLogger.warn(String.format("row < %s > has column of type <boolean> with non-<boolean> value - skipping column", lineNumber));
                }
                break;
            case DATETIME:
                try {
                    DateTimeFormatter isoDateFormatter = DateTimeFormatter.ISO_DATE;
                    String[] dt = cleanedValue.split("T");
                    LocalDate date = LocalDate.parse(dt[0], isoDateFormatter);
                    if (dt.length > 1) {
                        LocalTime time = LocalTime.parse(dt[1], DateTimeFormatter.ISO_TIME);
                        LocalDateTime dateTime = date.atTime(time);
                        att = unboundVar.eq(dateTime);
                    } else {
                        LocalDateTime dateTime = date.atStartOfDay();
                        att = unboundVar.eq(dateTime);
                    }
                } catch (DateTimeException dateTimeException) {
                    dataLogger.warn(String.format("row < %s > has column of type <datetime> with non-<ISO 8601 format> datetime value:  - < %s >", lineNumber, dateTimeException.getMessage()));
                }
                break;
            default:
                dataLogger.warn(String.format("row < %s > column type not valid - must be either: string, long, double, boolean, or datetime", lineNumber));
                break;
        }
        return att;
    }

    private static String applyPreprocessor(String cleanedValue,
                                            DataConfigEntry.DataConfigGeneratorMapping.PreprocessorConfig preprocessorConfig) {
        DataConfigEntry.DataConfigGeneratorMapping.PreprocessorConfig.PreprocessorParams params = preprocessorConfig.getParams();
        String processorType = preprocessorConfig.getType();
        switch (processorType) {
            case "regex":
                return applyRegexPreprocessor(cleanedValue, params.getRegexMatch(), params.getRegexReplace());
            default:
                throw new IllegalArgumentException("Preprocessor of type: <" + processorType + "> as specified in data config does not exist");
        }
    }

    private static String applyRegexPreprocessor(String stringToProcess,
                                                 String matchString,
                                                 String replaceString) {
        RegexPreprocessor rpp = new RegexPreprocessor(matchString, replaceString);
        return rpp.applyProcessor(stringToProcess);
    }
}
