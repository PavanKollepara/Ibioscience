package org.com.ideabytes.tse.dto;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;

import javax.imageio.ImageIO;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.com.ideabytes.exceptions.TSENestableException;
import org.com.ideabytes.git.Git_Integarte;
import org.com.ideabytes.resources.common.DBConstants;
import org.com.ideabytes.tse.DatabaseConnection;
import org.com.ideabytes.tse.JDBCTemplate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DataTransferConf implements DBConstants {
	public static final Log log = LogFactory.getLog(DataTransferConf.class);

	public String addProject(JSONObject inputData) {
		Connection con = null;
		String status = "Invalid Data";
		try {
			JSONArray JData = inputData.getJSONArray("data");
			JSONArray projectTestcases = new JSONArray();
			JSONArray requirementInfo = new JSONArray();
			JSONArray targetRemoteMachines = new JSONArray();
			JSONArray assignusers = new JSONArray();

			con = DatabaseConnection.getConnection();
			JDBCTemplate JDBCTemplate = new JDBCTemplate();
			String projectId = "";
			JSONObject project = new JSONObject();
			// set auto commit to false
			con.setAutoCommit(false);
			for (int i = 0; i < JData.length(); i++) {
				project = JData.getJSONObject(i);
				JSONArray projectTestcasesTemp = project.getJSONArray("project_test_cases");
				project.remove("project_test_cases");
				JSONArray requirementInfoTemp = project.getJSONArray("requirement_info");
				project.remove("requirement_info");
				JSONArray assign_userstemp = project.getJSONArray("assign_users");
				project.remove("assign_users");
				JSONArray targetRemoteMachinesTemp = project.getJSONArray("target_remote_machines");
				project.remove("target_remote_machines");

				// insert Project Information
				projectId = String.valueOf(JDBCTemplate.insert("PROJECTS_INFO", project, con));
				if (!(projectId.equalsIgnoreCase("0") || projectId.equalsIgnoreCase("-1"))) {

					if (projectTestcasesTemp.length() != 0) {
						projectTestcasesTemp = addKey(projectTestcasesTemp, "project_id", projectId);
						projectTestcases = addParentJsonArray(projectTestcases, projectTestcasesTemp);
					}
					if (requirementInfoTemp.length() != 0) {
						requirementInfoTemp = addKey(requirementInfoTemp, "project_id", projectId);
						requirementInfo = addParentJsonArray(requirementInfo, requirementInfoTemp);
					}
					if (targetRemoteMachinesTemp.length() != 0) {
						targetRemoteMachinesTemp = addKey(targetRemoteMachinesTemp, "project_id", projectId);
						targetRemoteMachines = addParentJsonArray(targetRemoteMachines, targetRemoteMachinesTemp);
					}
					if (assign_userstemp.length() != 0) {
						assign_userstemp = addKey(assign_userstemp, "project_id", projectId);
						assign_userstemp = addKey(assign_userstemp, "testsuite_id", "");
						assignusers = addParentJsonArray(assignusers, assign_userstemp);
					}
				} else {
					status = "Invalid Project";
				}

			}
			if (projectTestcases.length() != 0) {
				JDBCTemplate.insert("PROJECT_TESTCASES", projectTestcases, con);
			}
			if (requirementInfo.length() != 0) {
				JDBCTemplate.insert("REQUIREMENT_INFO", requirementInfo, con);
			}
			if (targetRemoteMachines.length() != 0) {
				JDBCTemplate.insert("TARGET_REMOTE_MACHINES", targetRemoteMachines, con);
			}
			if (assignusers.length() != 0) {
				JDBCTemplate.insert("PROJECT_ASSIGN_USER", assignusers, con);
			}
			// set auto commited to database
			insertRecentActivites("Project " + project.getString("project_name") + " was created",
					inputData.getString("userid"), con);
			con.commit();
			status = "Success_" + projectId;

		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while addProject method");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while addProject method");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return status;

	}

	private JSONArray convertJSONObjToArray(JSONObject inputData) {
		JSONArray result = new JSONArray();
		try {
			Iterator<String> keys = inputData.keys();
			while (keys.hasNext()) {
				String key = keys.next();
				result.put(inputData.get(key));

			}
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSON Exception caught while convertJSONObjToArray method");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while convertJSONObjToArray method");
		}
		return result;
	}

	/**
	 * This method will add elements to an array and return the resulting array
	 * 
	 * @param arr
	 * @param elements
	 * @return
	 */
	public JSONArray addParentJsonArray(JSONArray ParentArry, JSONArray childArray) {
		try {
			for (int i = 0; i < childArray.length(); i++) {
				JSONObject childObj = childArray.getJSONObject(i);
				if (childObj.has("Automation_Feasibility")) {
					if (childObj.getString("Automation_Feasibility").equalsIgnoreCase("Yes"))
						childObj.put("Automation_Feasibility", "1");
					else
						childObj.put("Automation_Feasibility", "0");
				}
				ParentArry.put(childObj);
			}
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("Exception caught while addParentJsonArray method" + " message: " + e.getMessage());
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"Exception caught while addParentJsonArray method");
		}
		return ParentArry;
	}

	/**
	 * This method will add elements to an array and return the resulting array
	 * 
	 * @param arr
	 * @param elements
	 * @return
	 */
	public JSONArray addKey(JSONArray arr, String keyName, Object value) {
		try {
			for (int i = 0; i < arr.length(); i++) {
				arr.getJSONObject(i).put(keyName, value);
			}
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("Exception caught while addKey method" + " message: " + e.getMessage());
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"Exception caught while addKey method");
		}
		return arr;

	}

	public JSONArray loadProjectInfo(String userId, String start, String length, String column, String dir) {
		Connection con = null;
		JSONArray projectInfo = new JSONArray();
		try {
			con = DatabaseConnection.getConnection();
			JDBCTemplate JDBCTemplate = new JDBCTemplate();
			// set auto commit to false
			con.setAutoCommit(false);

			String userDetailsQuery = "SELECT(SELECT Count(*) FROM `PROJECTS_INFO` WHERE `PROJECT_ID` IN (SELECT DISTINCT `PROJECT_ID` FROM `PROJECT_ASSIGN_USER` WHERE `USER_ID`='"
					+ userId + "' AND `TESTSUITE_ID` ='') AND `PROJECT_STATUS` = '1') as count,"
					+ " `PROJECT_ID`,`PROJECT_NAME`,`PROJECT_DESC` FROM `PROJECTS_INFO` WHERE `PROJECT_ID` IN (SELECT DISTINCT `PROJECT_ID` FROM `PROJECT_ASSIGN_USER` WHERE `USER_ID`='"
					+ userId + "' AND `TESTSUITE_ID` ='') AND `PROJECT_STATUS` = '1' ORDER BY `" + column + "` " + dir
					+ " LIMIT " + start + "," + length;
			projectInfo = JDBCTemplate.select(userDetailsQuery, con);
			System.out.println("userDetailsQuery:" + userDetailsQuery);
			// set auto commited to database
			con.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while loadProjectInfo");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught loadProjectInfo");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while loadProjectInfo");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while loadProjectInfo");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return projectInfo;
	}

	public String updateStriptMichineData(JSONArray inputData) {
		Connection con = null;
		String status = "Success";
		JSONObject projectInfo = new JSONObject();
		try {
			con = DatabaseConnection.getConnection();
			JDBCTemplate JDBCTemplate = new JDBCTemplate();
			// set auto commit to false
			con.setAutoCommit(false);
			JSONArray updateDataArray = new JSONArray();
			JSONArray insertDataArray = new JSONArray();
			for (int i = 0; i < inputData.length(); i++) {
				JSONObject remoteMichineData = inputData.getJSONObject(i);
				if(remoteMichineData.has("$$hashKey"))
				{
					remoteMichineData.remove("$$hashKey");	
				}
				System.out.println("remoteMichineData:"+remoteMichineData);
				String targetRemoteMachineId = remoteMichineData.getString("target_remote_machine_id");
				remoteMichineData.remove("target_remote_machine_id");
				if (!targetRemoteMachineId.equalsIgnoreCase("")) {
					JSONObject whereCondition = new JSONObject();
					JSONObject upDateObj = new JSONObject();
					whereCondition.put("target_remote_machine_id", targetRemoteMachineId);
					upDateObj.put("filter_data", whereCondition);
					upDateObj.put("update_data", remoteMichineData);
					updateDataArray.put(upDateObj);
				} else {
					insertDataArray.put(remoteMichineData);
				}

			}
			if (updateDataArray.length() != 0) {
				JDBCTemplate.update("TARGET_REMOTE_MACHINES", updateDataArray, con);
			}
			if (insertDataArray.length() != 0) {
				JDBCTemplate.insert("TARGET_REMOTE_MACHINES", insertDataArray, con);
			}
			// set auto commited to database
			con.commit();
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while updateStriptMichineData");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while updateStriptMichineData");
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while updateStriptMichineData");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught loadProjectInfo");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while updateStriptMichineData");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while updateStriptMichineData");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return status;
	}

	public int updateDefecttols(JSONObject object) {

		JSONObject whereCondition = new JSONObject();
		JSONObject upDateObj = new JSONObject();
		JSONArray updateDataArray = new JSONArray();
		Connection con = DatabaseConnection.getConnection();
		try {
			whereCondition.put("project_id", object.getString("project_id"));
			upDateObj.put("filter_data", whereCondition);
			object.remove("project_id");
			upDateObj.put("update_data", object);
			updateDataArray.put(upDateObj);
			con.setAutoCommit(false);
			int status = new JDBCTemplate().update("PROJECTS_INFO", updateDataArray, con);
			con.commit();
			return status;

		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while updateDefecttols");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while updateDefecttols");
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while updateDefecttols");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught updateDefecttols");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while updateDefecttols");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while updateDefecttols");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}
	}

	public String uploadTestcases(JSONObject object) {
		JSONObject responce = new JSONObject();
		Connection con = null;
		String status = "Failure";
		try {
			JSONArray insertArray = null;
			JSONArray updateArray = null;
			System.out.println("JsonObject:" + object);
			JSONArray array = object.getJSONArray("project_test_cases");
			String projectId = object.getString("project_id");
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			con = DatabaseConnection.getConnection();
			con.setAutoCommit(false);
			String inParameter = "";
			boolean plaintext = false;
			String TESTTYPE = "AUTO";

			// uploadManualTestcases(object,con,plaintext);

			if (array.length() != 0) {
				for (int i = 0; i < array.length(); i++) {
					String testcaseName = array.getJSONObject(i).getString("testcasename");
					if (!inParameter.equalsIgnoreCase(""))
						inParameter = inParameter + ",'" + testcaseName + "'";
					else
						inParameter = "'" + testcaseName + "'";
				}
				if (object.has("manual_execution")) {
					TESTTYPE = "MANUAL";
				}

				String query = "SELECT DISTINCT TESTCASENAME FROM PROJECT_TESTCASES where TESTCASENAME IN("
						+ inParameter + ") AND `PROJECT_ID` = '" + projectId + "' AND `TESTTYPE`='" + TESTTYPE + "'";
				JSONArray duplicatesArray = jdbcTemplate.select(query, con);

				insertArray = new JSONArray();
				updateArray = new JSONArray();

				for (int i = 0; i < array.length(); i++) {
					JSONObject inputTestCaseObj = array.getJSONObject(i);
					inputTestCaseObj.put("project_id", projectId);
					String inputTestCaseName = inputTestCaseObj.getString("testcasename");
					boolean isExist = false;
					for (int j = 0; j < duplicatesArray.length(); j++) {
						String dbTestCaseName = duplicatesArray.getJSONObject(j).getString("testcasename");
						if (dbTestCaseName.equalsIgnoreCase(inputTestCaseName)) {
							isExist = true;
							break;
						}
					}

					if (isExist) {
						inputTestCaseObj.remove("testcasename");
						JSONObject whereCondition = new JSONObject();
						JSONObject upDateObj = new JSONObject();
						whereCondition.put("testcasename", inputTestCaseName);
						whereCondition.put("project_id", projectId);
						upDateObj.put("filter_data", whereCondition);
						upDateObj.put("update_data", inputTestCaseObj);
						updateArray.put(upDateObj);
					} else {
						insertArray.put(inputTestCaseObj);
					}
				}

				if (updateArray != null && updateArray.length() != 0) {
					jdbcTemplate.update("PROJECT_TESTCASES", updateArray, con);
				}
				if (insertArray != null && insertArray.length() != 0) {
					jdbcTemplate.insert("PROJECT_TESTCASES", insertArray, con);
				}
				String getGitdetailsqry = "SELECT `PROJECT_NAME`,`GIT_URL`,`GIT_USERNAME`,`GIT_PASSWORD`,`GIT_FILENAME`,`GIT_SHEETNAME` FROM `PROJECTS_INFO` WHERE `PROJECT_ID`='"
						+ projectId + "'";
				System.out.println("getGitdetailsqry:" + getGitdetailsqry);

				JSONArray gitdetailsarray = jdbcTemplate.select(getGitdetailsqry, con);

				/*
				 * Git_Integarte gitiIntegarte = new Git_Integarte();
				 * 
				 * for (int i = 0; i < gitdetailsarray.length(); i++) { JSONObject gitobject =
				 * gitdetailsarray.getJSONObject(i);
				 * if(!gitobject.getString("git_url").equalsIgnoreCase("")&&!gitobject.getString
				 * ("git_url").equalsIgnoreCase(null)) {
				 * gitiIntegarte.commit(gitobject.getString("git_url"),
				 * gitobject.getString("git_username"), gitobject.getString("git_password"),
				 * gitobject.getString("git_filename"), gitobject.getString("git_sheetname"),
				 * projectId,gitobject.getString("project_name")); } }
				 */
				status = "Success";

			} else {
				status = "Failure";
			}
			con.commit();
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while uploadTestcases");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while uploadTestcases");
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while uploadTestcases");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught whileuploadTestcases");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while uploadTestcases");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while uploadTestcases");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}
		return status;
	}

	public String uploadManualTestcases(JSONObject inputobj) {
		String status = "";
		Connection con = DatabaseConnection.getConnection();
		try {
			JSONArray tescaseTableData = new JSONArray();
			JSONArray manualtescaseTableData = new JSONArray();

			JSONObject testDataObj = new JSONObject();
			String project_id = inputobj.getString("project_id");
			JSONArray inputtestcasearray = inputobj.getJSONArray("project_test_cases");
			LinkedHashSet<String> headerset = new LinkedHashSet<>();
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			boolean isNotPlaneTesxt = inputobj.getBoolean("isnotplanetext");

			con.setAutoCommit(false);
			for (int i = 0; i < inputtestcasearray.length(); i++) {
				JSONObject inputTestCaseObj = inputtestcasearray.getJSONObject(i);
				String testcaseName = inputTestCaseObj.getString("testcasename");

				JSONObject tempTestData = null;
				if (!testDataObj.has(testcaseName)) {
					// TO add all Tescase tabe required Information to TestCaseObj
					JSONObject jData = new JSONObject();
					jData.put("testcasename", inputTestCaseObj.getString("testcasename"));
					jData.put("project_id", inputTestCaseObj.getString("project_id"));
					jData.put("created_user_id", inputTestCaseObj.getString("created_user_id"));
					jData.put("sno", inputTestCaseObj.getString("sno"));
					jData.put("assign_status", inputTestCaseObj.getString("assign_status"));
					jData.put("description", inputTestCaseObj.getString("description"));
					jData.put("testtype", inputTestCaseObj.getString("testtype"));
					jData.put("requirement", inputTestCaseObj.getString("requirement"));
					jData.put("module", inputTestCaseObj.getString("module"));
					jData.put("release_version", inputTestCaseObj.getString("release_version"));
					jData.put("designer", inputTestCaseObj.getString("designer"));
					jData.put("priority", inputTestCaseObj.getString("priority"));
					jData.put("execution_type", inputTestCaseObj.getString("execution_type"));
					jData.put("automation_feasibility", inputTestCaseObj.getString("automation_feasibility"));
					jData.put("test_step_number", inputTestCaseObj.getString("test_step_number"));
					if (isNotPlaneTesxt) {
						jData.put("test_step", generateTestStep(inputTestCaseObj.getString("test_step")));
					} else {
						jData.put("test_step", inputTestCaseObj.getString("test_step"));

					}

					tempTestData = new JSONObject();
					tescaseTableData.put(jData);
				} else {
					tempTestData = testDataObj.getJSONObject(testcaseName);
					// TEST_STEP_NOTIFIER
				}

				String testStep = inputTestCaseObj.getString("test_step");
				HashMap<String, String> keyvaluepairmap = KeyMapGenerator(testStep);
				if (keyvaluepairmap.size() > 0) {
					Set<String> keys = keyvaluepairmap.keySet();
					for (String key : keys) {
						headerset.add(key);
						tempTestData.put(key, keyvaluepairmap.get(key));
					}

				}
				tempTestData.put("Test_Case_Name", testcaseName);
				testDataObj.put(testcaseName, tempTestData);

				inputTestCaseObj.put("test_step_notifier", inputTestCaseObj.getString("test_step"));
				inputTestCaseObj.put("test_step", generateTestStep(inputTestCaseObj.getString("test_step")));
				manualtescaseTableData.put(inputTestCaseObj);

			}

			/// insert manual testcase
			// jdbcTemplate.insert("PROJECT_MANUAL_TESTCASES", manualtescaseTableData, con);
			String inParameter = "";
			if (manualtescaseTableData.length() != 0) {
				for (int i = 0; i < manualtescaseTableData.length(); i++) {
					String testcaseName = manualtescaseTableData.getJSONObject(i).getString("testcasename");
					if (!inParameter.equalsIgnoreCase(""))
						inParameter = inParameter + ",'" + testcaseName + "'";
					else
						inParameter = "'" + testcaseName + "'";
				}
				String query = "SELECT DISTINCT TESTCASENAME,TEST_STEP_NUMBER FROM PROJECT_MANUAL_TESTCASES where TESTCASENAME IN("
						+ inParameter + ") AND `PROJECT_ID` = '" + project_id + "'";
				JSONArray duplicatesArray = jdbcTemplate.select(query, con);

				JSONArray insertArray = new JSONArray();
				JSONArray updateArray = new JSONArray();

				for (int i = 0; i < manualtescaseTableData.length(); i++) {
					JSONObject inputTestCaseObj = manualtescaseTableData.getJSONObject(i);
					inputTestCaseObj.put("project_id", project_id);
					String inputTestCaseName = inputTestCaseObj.getString("testcasename");
					String inputtest_step_number = inputTestCaseObj.getString("test_step_number");
					boolean isExist = false;
					for (int j = 0; j < duplicatesArray.length(); j++) {
						String dbTestCaseName = duplicatesArray.getJSONObject(j).getString("testcasename");
						String dbtest_step_number = duplicatesArray.getJSONObject(j).getString("test_step_number");

						if (dbTestCaseName.equalsIgnoreCase(inputTestCaseName)&&dbtest_step_number.equalsIgnoreCase(inputtest_step_number)) {
							isExist = true;
							break;
						}
					}

					if (isExist) {
						//inputTestCaseObj.remove("testcasename");
						JSONObject whereCondition = new JSONObject();
						JSONObject upDateObj = new JSONObject();
						whereCondition.put("testcasename", inputTestCaseName);
						whereCondition.put("project_id", project_id);
						upDateObj.put("filter_data", whereCondition);
						upDateObj.put("update_data", inputTestCaseObj);
						updateArray.put(upDateObj);
					} else {
						insertArray.put(inputTestCaseObj);
					}
				}
				System.out.println("updateArray:"+updateArray);

				if (updateArray != null && updateArray.length() != 0) {
					jdbcTemplate.update("PROJECT_MANUAL_TESTCASES", updateArray, con);
				}
				if (insertArray != null && insertArray.length() != 0) {
					jdbcTemplate.insert("PROJECT_MANUAL_TESTCASES", insertArray, con);
				}
			}

			// insert Testcases
			JSONObject testcaseobj = new JSONObject();
			testcaseobj.put("project_id", project_id);
			testcaseobj.put("project_test_cases", tescaseTableData);
			testcaseobj.put("manual_execution", "yes");
			uploadTestcases(testcaseobj);
			//
			System.out.println("testDataObj:" + testDataObj);

			JSONArray testdataarraty = new JSONArray();
			JSONArray headersarray = new JSONArray();

			Iterator<String> testdatakeys = testDataObj.keys();
			while (testdatakeys.hasNext()) {
				testdataarraty.put(testDataObj.getJSONObject(testdatakeys.next()));
			}
			JSONObject inserttestdata = new JSONObject();
			inserttestdata.put("test_data", testdataarraty);
			inserttestdata.put("project_id", project_id);
			Iterator<String> iter = headerset.iterator();
			while (iter.hasNext()) {

				headersarray.put(iter.next());
			}
			headersarray.put("Test_Case_Name");
			inserttestdata.put("header_data", headersarray);

			// insert testData\
			if(!isNotPlaneTesxt)
			{
			insertOrUpdateProjectTestDataHeader(inserttestdata, con);
			insertTestData(inserttestdata, con);
			}

			// jdbcTemplate.insert("", testDataObj, con);

			con.commit();
			status = "Success";

		} catch (Exception e) {
			e.printStackTrace();
			log.error("JSONException caught while uploadTestcases");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while uploadTestcases");
		}
		return status;

	}

	public JSONArray getManualTestcaseDetails(JSONObject mainobject) {
		Connection con = null;
		JSONArray projectInfo = new JSONArray();
		JSONArray testcasedeatils = new JSONArray();
		JSONObject testcasedeatilsobj = new JSONObject();

		try {
			con = DatabaseConnection.getConnection();
			JSONArray testcasearray = mainobject.getJSONArray("testcase_name");
			String project_id = mainobject.getString("project_id");
			StringBuffer testsuite = new StringBuffer();
			String testcase_name = "";
			if (testcasearray.length() != 0) {
				for (int i = 0; i < testcasearray.length(); i++) {
					testsuite.append("'" + testcasearray.getString(i) + "',");
				}
				testcase_name = testsuite.toString();
				testcase_name = testcase_name.substring(0, testcase_name.length() - 1);
			}

			JDBCTemplate JDBCTemplate = new JDBCTemplate();

			String userDetailsQuery = "SELECT TEST_STEP,TESTCASENAME,TEST_STEP_NOTIFIER,TEST_STEP_NUMBER,EXPECTED_RESULT FROM `PROJECT_MANUAL_TESTCASES` WHERE `PROJECT_ID`='"
					+ project_id + "' and TESTCASENAME IN(" + testcase_name + ")";
			System.out.println("userDetailsQuery:" + userDetailsQuery);

			projectInfo = JDBCTemplate.select(userDetailsQuery, con);
			if (projectInfo.length() != 0) {
				for (int i = 0; i < projectInfo.length(); i++) {
					JSONObject object = projectInfo.getJSONObject(i);
					String test_step_notifier = object.getString("test_step_notifier");
					String testcasename = object.getString("testcasename");

					if (!testcasedeatilsobj.has(testcasename)) {

						JSONObject stepsobj = new JSONObject();
						JSONArray mainstepsobj = new JSONArray();
						//JSONArray paramsarray = new JSONArray();

						JSONObject instepsobj = new JSONObject();

						/*
						 * HashMap<String, HashMap<Integer, String>> map =
						 * generateMaps(test_step_notifier); HashMap<Integer, String> keymap =
						 * map.get("keymap"); HashMap<Integer, String> parammap = map.get("parammap");
						 * 
						 * for (int k = 0; k < keymap.size(); k++) { JSONObject field = new
						 * JSONObject(); field.put("Field", keymap.get(k).toString().trim());
						 * field.put("Value", parammap.get(k).toString().trim());
						 * paramsarray.put(field);
						 * 
						 * }
						 */
						instepsobj.put("teststep_number", object.getString("test_step_number"));
						instepsobj.put("user_action", object.getString("expected_result"));
						instepsobj.put("test_step", object.getString("test_step"));
						//instepsobj.put("field_array", paramsarray);
						mainstepsobj.put(instepsobj);
						stepsobj.put("testcasename", testcasename);
						stepsobj.put("steps", mainstepsobj);

						testcasedeatilsobj.put(testcasename, stepsobj);

					} else {
						JSONObject tempsteps = testcasedeatilsobj.getJSONObject(testcasename);
						JSONArray tempstepsobj = tempsteps.getJSONArray("steps");
						JSONObject instepsobj = new JSONObject();
						JSONArray paramsarray = new JSONArray();

						instepsobj.put("user_action", object.getString("expected_result"));
						instepsobj.put("test_step", object.getString("test_step"));
						instepsobj.put("teststep_number", object.getString("test_step_number"));


						/*
						 * HashMap<String, HashMap<Integer, String>> map =
						 * generateMaps(test_step_notifier); HashMap<Integer, String> keymap =
						 * map.get("keymap"); HashMap<Integer, String> parammap = map.get("parammap");
						 * for (int k = 0; k < keymap.size(); k++) { JSONObject field = new
						 * JSONObject(); field.put("Field", keymap.get(k).toString().trim());
						 * field.put("Value", parammap.get(k).toString().trim());
						 * paramsarray.put(field);
						 * 
						 * } instepsobj.put("field_array", paramsarray);
						 */
						tempstepsobj.put(instepsobj);
						tempsteps.put("steps", tempstepsobj);

						testcasedeatilsobj.put(testcasename, tempsteps);

					}

				}
			}
			System.out.println(testcasedeatilsobj);
			Iterator<String> itr = testcasedeatilsobj.keys();
			while (itr.hasNext()) {

				testcasedeatils.put(testcasedeatilsobj.getJSONObject(itr.next()));
			}
			System.out.println(testcasedeatils);

		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getManualTestcaseDetails");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getManualTestcaseDetails");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return testcasedeatils;
	}

	public HashMap<String, HashMap<Integer, String>> generateMaps(String step2) {
		HashMap<Integer, String> keymap = new HashMap<>();
		HashMap<Integer, String> parammap = new HashMap<>();
		HashMap<String, HashMap<Integer, String>> mainmap = new HashMap<>();

		String[] step = step2.split(" ");
		int i = 0;
		for (String step1 : step) {

			if (step1.contains("$")) {

				String[] str = step1.split("$");
				if (str[0].contains("<<<")) {
					String[] s2 = str[0].split("<<<");
					keymap.put(i, s2[0].replace("$", "").trim());
					parammap.put(i, s2[1].replace(">>>", "").trim());
					i++;

				}

			}
		}
		System.out.println(keymap);
		System.out.println(parammap);
		mainmap.put("keymap", keymap);
		mainmap.put("parammap", parammap);

		return mainmap;
	}

	public String generateTestStep(String step2) {
		String[] step = step2.split(" ");
		StringBuilder builder = new StringBuilder();
		for (String step1 : step) {
			if (step1.contains("$")) {
				if (step1.contains("<<<")) {
					String[] str = step1.split("<<<");
					builder = builder.append(str[0].replace("$", " ").trim());
					builder = builder.append(" ");
				}
			} else {
				builder = builder.append(step1);
				builder = builder.append(" ");
			}
		}
		return builder.toString();
	}

	private HashMap<String, String> KeyMapGenerator(String test_step) {
		HashMap<String, String> keyvalue = new HashMap<>();

		try {

			String[] step = test_step.split(" ");
			for (String step1 : step) {

				if (step1.contains("$")) {

					String[] str = step1.split("$");
					if (str[0].contains("<<<")) {
						String[] s2 = str[0].split("<<<");
						String key = s2[0].replace("$", "").trim();
						String value = s2[1].replace(">>>", "").trim();
						keyvalue.put(key, value);

					}

				}
			}

		} catch (Exception e) {

			e.printStackTrace();
		}

		return keyvalue;
	}

	public String deleteUser(String userId, String login_id, String user_name) {
		String response = "99";
		Connection con = null;
		try {
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			con = DatabaseConnection.getConnection();
			con.setAutoCommit(false);
			if (!userId.equalsIgnoreCase("") && !login_id.equalsIgnoreCase("") && !user_name.equalsIgnoreCase("")) {
				String query = "UPDATE `USER_DETAILS` SET `ACTIVE_STATUS` = '0' WHERE `USER_ID` ='" + userId + "'";
				jdbcTemplate.updateByQuery(query, con);
				String deleteprojectusers = "DELETE FROM `PROJECT_ASSIGN_USER` WHERE `USER_ID`='" + userId + "'";
				jdbcTemplate.delete(deleteprojectusers, con);
				response = "00";

				insertRecentActivites("User " + user_name + " was deleted", login_id, con);

			}
			con.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while deleteUser");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught deleteUser");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while deleteUser");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while deleteUser");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}

		return response;
	}

	public String deleteTestSuite(String testsuit_id, String user_id, String testsuite_name) {
		String response = "99";
		Connection con = null;
		try {
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			con = DatabaseConnection.getConnection();
			con.setAutoCommit(false);
			if (!testsuit_id.equalsIgnoreCase("") && !user_id.equalsIgnoreCase("")
					&& !testsuite_name.equalsIgnoreCase("")) {
				String query = "UPDATE `TESTSUITE_DETAILS` SET `TESTSUITE_STATUS` = '0' WHERE `TESTSUITE_ID` ='"
						+ testsuit_id + "'";
				jdbcTemplate.updateByQuery(query, con);
				response = "00";
				insertRecentActivites("TestSuite " + testsuite_name + " was deleted", user_id, con);

			}
			con.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while deleteTestSuite");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught while deleteTestSuite");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while deleteTestSuite");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while deleteTestSuite");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}

		return response;
	}

	public String insertprojectAssigneUsers(JSONObject object) {
		String response = "";
		Connection con = null;
		try {
			JSONArray arrayObje = object.getJSONArray("user_data");
			String projectId = object.getString("project_id");
			String testsuite_id = object.getString("testsuite_id");
			for (int i = 0; i < arrayObje.length(); i++) {

				arrayObje.getJSONObject(i).put("project_id", projectId);
				arrayObje.getJSONObject(i).put("testsuite_id", testsuite_id);
				if (arrayObje.getJSONObject(i).has("project_assign_user_id")) {
					arrayObje.getJSONObject(i).remove("project_assign_user_id");
				}
				if (arrayObje.getJSONObject(i).has("username")) {
					arrayObje.getJSONObject(i).remove("username");
				}
			}
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			con = DatabaseConnection.getConnection();
			con.setAutoCommit(false);
			if (arrayObje.length() != 0) {
				String query = "DELETE FROM `PROJECT_ASSIGN_USER` WHERE `PROJECT_ID`='" + projectId
						+ "' AND `TESTSUITE_ID` = '" + testsuite_id + "'";
				jdbcTemplate.delete(query, con);
				jdbcTemplate.insert("PROJECT_ASSIGN_USER", arrayObje, con);
				response = "Success";

			}
			con.commit();
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while insertprojectAssigneUsers");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while insertprojectAssigneUsers");
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while insertprojectAssigneUsers");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught while insertprojectAssigneUsers");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while insertprojectAssigneUsers");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while insertprojectAssigneUsers");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}

		return response;
	}

//project_assign_user_id
	public int deleteUserFromProject(JSONObject object) {
		int result = 0;
		Connection con = null;
		try {
			con = DatabaseConnection.getConnection();
			con.setAutoCommit(false);
			JDBCTemplate template = new JDBCTemplate();
			result = template.delete("PROJECT_ASSIGN_USER", object, con);
			con.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while deleteUserFromProject");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught while deleteUserFromProject");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while deleteUserFromProject");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while deleteUserFromProject");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}

		return result;
	}

	public JSONArray getUserslist(String start, String length, String column, String dir, String userid) {
		Connection con = null;
		JSONArray userDetails = new JSONArray();
		try {
			con = DatabaseConnection.getConnection();
			JDBCTemplate JDBCTemplate = new JDBCTemplate();
			// set auto commit to false
			con.setAutoCommit(false);
			String userlistqry = "";
			if (userid.equalsIgnoreCase("") || userid.equalsIgnoreCase("0")) {
				userlistqry = "SELECT *,(SELECT COUNT(*)  FROM USER_DETAILS  WHERE `ACTIVE_STATUS` = '1') as count FROM USER_DETAILS  WHERE `ACTIVE_STATUS` = '1' ORDER BY `"
						+ column + "` " + dir + " LIMIT " + start + "," + length;
			} else {
				userlistqry = "SELECT * FROM USER_DETAILS WHERE `ACTIVE_STATUS` = '1' AND `USER_ID`='" + userid
						+ "' ORDER BY `" + column + "` " + dir + " LIMIT " + start + "," + length;

			}
			System.out.println("userlistqry:" + userlistqry);
			userDetails = JDBCTemplate.select(userlistqry, con);
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while getUserslist");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught while getUserslist");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getUserslist");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getUserslist");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return userDetails;
	}

	public JSONObject loginVerification(JSONObject inputData) {
		Connection con = null;
		JSONObject responce = new JSONObject();
		String status = "";
		try {
			con = DatabaseConnection.getConnection();
			JDBCTemplate JDBCTemplate = new JDBCTemplate();
			// set auto commit to false
			con.setAutoCommit(false);
			String userMail = inputData.getString("username");
			String password = inputData.getString("password");
			String sessionId = inputData.getString("session_id");
			String userId = "";
			String userName = "";

			String fourceLogineStatus = inputData.getString("fource_login_status");
			/*
			 * String selectUserDeta =
			 * "SELECT * FROM `USER_DETAILS` WHERE `USER_EMAIL_ID` = '" + userMail +
			 * "' AND `PASSWORD` = '" + password + "' AND `ACTIVE_STATUS`='1'";
			 */

			// Code changed by Pavan 30-04-2019 for login with both username and mailid

			String selectUserDeta = "SELECT * FROM `USER_DETAILS` WHERE (`USER_EMAIL_ID` = '" + userMail
					+ "' OR `USERNAME` = '" + userMail + "') AND `PASSWORD` = '" + password
					+ "' AND `ACTIVE_STATUS`='1'";

			String loginStatus = "1";
			JSONArray userDetails = JDBCTemplate.select(selectUserDeta, con);
			if (userDetails.length() == 0) {
				status = "NOTEXIST";
			} else {
				JSONObject userData = userDetails.getJSONObject(0);
				String db_session_id = userData.getString("session_id");
				if (sessionId.equalsIgnoreCase("")) {
					userId = userData.getString("user_id");
					userName = userData.getString("username");
					status = "SUCCESS";
					loginStatus = "0";
				} else if (db_session_id.equalsIgnoreCase("")) {
					userId = userData.getString("user_id");
					userName = userData.getString("username");
					status = "SUCCESS";
				} else if (userData.getString("logged_status").equalsIgnoreCase("1")
						&& !db_session_id.equalsIgnoreCase(sessionId) && fourceLogineStatus.equalsIgnoreCase("0")) {
					status = "ALLREADYLOGIN";
				} else if (userData.getString("logged_status").equalsIgnoreCase("1")
						&& userData.getString("session_id").equalsIgnoreCase(sessionId)
						&& fourceLogineStatus.equalsIgnoreCase("0")) {
					status = "ACTIVEUSER";
					userId = userData.getString("user_id");
					userName = userData.getString("username");

				} else {
					userId = userData.getString("user_id");
					userName = userData.getString("username");

					status = "SUCCESS";
				}

			}
			if (status.equalsIgnoreCase("SUCCESS")) {
				String updateQuery = "UPDATE `USER_DETAILS` SET `LOGGED_STATUS`= '" + loginStatus + "',`SESSION_ID`= '"
						+ sessionId + "',`LOGIN_TIME`= NOW(),`FIRST_TIME`='2' WHERE `USER_ID`= '" + userId + "'";
				JDBCTemplate.updateByQuery(updateQuery, con);
			}
			responce.put("status", status);
			responce.put("user_id", userId);
			responce.put("username", userName);

			// set auto commited to database
			con.commit();
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while loadProjectInfo method");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while loadProjectInfo method");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return responce;
	}

	public JSONArray getUserslist() {
		Connection con = null;
		JSONArray userDetails = new JSONArray();
		try {
			con = DatabaseConnection.getConnection();
			JDBCTemplate JDBCTemplate = new JDBCTemplate();
			// set auto commit to false
			con.setAutoCommit(false);
			String userlistqry = "SELECT * FROM USER_DETAILS where `ACTIVE_STATUS`='1'";
			userDetails = JDBCTemplate.select(userlistqry, con);
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while getUserslist");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught while getUserslist");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getUserslist");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getUserslist");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return userDetails;
	}

	public JSONObject getProjectAssignedUsers(JSONObject object) {
		Connection con = null;
		JSONArray userDetails = new JSONArray();
		JSONArray assignArray = new JSONArray();
		JSONArray unAssignArray = new JSONArray();

		try {
			con = DatabaseConnection.getConnection();
			JDBCTemplate JDBCTemplate = new JDBCTemplate();
			// set auto commit to false
			con.setAutoCommit(false);
			String assignusers = "SELECT PA.PROJECT_ASSIGN_USER_ID AS PROJECT_ASSIGN_USER_ID, PA.USER_ID, PA.ADMIN_TYPE,UD.USERNAME FROM PROJECT_ASSIGN_USER AS PA INNER JOIN USER_DETAILS AS UD ON PA.USER_ID=UD.USER_ID WHERE PROJECT_ID='"
					+ object.getString("project_id") + "' AND TESTSUITE_ID ='" + object.getString("testsuite_id")
					+ "' AND UD.`ACTIVE_STATUS` = '1'";
			assignArray = JDBCTemplate.select(assignusers, con);
			String unassignusers = "SELECT USER_ID,ADMIN_TYPE,USERNAME FROM USER_DETAILS where USER_ID NOT IN(SELECT USER_ID from PROJECT_ASSIGN_USER where PROJECT_ID='"
					+ object.getString("project_id") + "' AND TESTSUITE_ID ='" + object.getString("testsuite_id")
					+ "') AND `ACTIVE_STATUS` = '1'";
			unAssignArray = JDBCTemplate.select(unassignusers, con);
			object.put("assignUsers", assignArray);
			object.put("unassignUsers", unAssignArray);
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while getProjectAssignedUsers");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught while getProjectAssignedUsers");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getProjectAssignedUsers");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getProjectAssignedUsers");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return object;
	}

	public JSONArray getTestsuiteDetails(String projectId, String userId, String start, String length, String column,
			String dir) {
		Connection con = null;
		JSONArray testsuiteInfo = new JSONArray();
		try {
			con = DatabaseConnection.getConnection();
			JDBCTemplate JDBCTemplate = new JDBCTemplate();
			// set auto commit to false
			con.setAutoCommit(false);
			String testsuiteDetailsQuery = "";
			testsuiteDetailsQuery = "SELECT * FROM `TESTSUITE_DETAILS` WHERE `TESTSUITE_ID` IN "
					+ "(SELECT DISTINCT `TESTSUITE_ID` FROM `PROJECT_ASSIGN_USER` " + "WHERE `PROJECT_ID` = '"
					+ projectId + "' AND `USER_ID` ='" + userId + "' AND `TESTSUITE_ID` != '') "
					+ "AND `TESTSUITE_STATUS` = '1' ORDER BY `" + column + "` " + dir + " LIMIT " + start + ","
					+ length;
			testsuiteInfo = JDBCTemplate.select(testsuiteDetailsQuery, con);
			// set auto commited to database
			con.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while getTestsuiteDetails");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught loadProjectInfo");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getTestsuiteDetails");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getTestsuiteDetails");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return testsuiteInfo;
	}

	public String addTesuSuits(JSONObject inputData) {
		Connection con = null;
		String status = "Invalid Data";
		String testsuite_id = "";
		try {
			JSONArray JData = inputData.getJSONArray("data");
			JSONArray assignusers = new JSONArray();
			con = DatabaseConnection.getConnection();
			JDBCTemplate JDBCTemplate = new JDBCTemplate();
			// set auto commit to false
			JSONObject test_suite_data = new JSONObject();
			con.setAutoCommit(false);
			for (int i = 0; i < JData.length(); i++) {
				test_suite_data = JData.getJSONObject(i);
				JSONArray assign_userstemp = test_suite_data.getJSONArray("assign_users");
				test_suite_data.remove("assign_users");
				// insert Project Information
				testsuite_id = String.valueOf(JDBCTemplate.insert("TESTSUITE_DETAILS", test_suite_data, con));
				if (assign_userstemp.length() != 0) {
					String projectId = test_suite_data.getString("project_id");
					assign_userstemp = addKey(assign_userstemp, "project_id", projectId);
					assign_userstemp = addKey(assign_userstemp, "testsuite_id", testsuite_id);
					assignusers = addParentJsonArray(assignusers, assign_userstemp);
				}

			}
			if (assignusers.length() != 0) {
				JDBCTemplate.insert("PROJECT_ASSIGN_USER", assignusers, con);
			}
			// set auto commited to database
			insertRecentActivites("TestSuite " + test_suite_data.getString("testsuite_name") + " was created",
					test_suite_data.getString("user_id"), con);
			con.commit();
			status = testsuite_id;

		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while addProject method");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while addProject method");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return status;

	}

	public String insertOrUpdateAssigneTestCasesMapper(JSONObject object) {
		String response = "";
		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			con = DatabaseConnection.getConnection();
			JSONArray array = object.getJSONArray("data");
			JDBCTemplate jdbcTemplate = new JDBCTemplate();

			String inParameter = "";
			con.setAutoCommit(false);
			if (array.length() != 0) {
				for (int i = 0; i < array.length(); i++) {
					if (array.getJSONObject(i).has("testcasename")) {
						array.getJSONObject(i).put("testcase_name", array.getJSONObject(i).getString("testcasename"));
						array.getJSONObject(i).remove("testcasename");
					}
					String testcaseName = array.getJSONObject(i).getString("testcase_name");
					if (!inParameter.equalsIgnoreCase(""))
						inParameter = inParameter + ",'" + testcaseName + "'";
					else
						inParameter = "'" + testcaseName + "'";
				}
				String query = "SELECT DISTINCT `PROJECT_TESTCASE_ID`,`TESTCASENAME` FROM PROJECT_TESTCASES where TESTCASENAME IN("
						+ inParameter + ") AND `PROJECT_ID` = '" + array.getJSONObject(0).getString("project_id") + "'";
				ps = con.prepareStatement(query);
				rs = ps.executeQuery();
				JSONObject testcaseIdObj = new JSONObject();
				while (rs.next()) {
					testcaseIdObj.put(rs.getString("TESTCASENAME"), rs.getString("PROJECT_TESTCASE_ID"));
				}
				if (testcaseIdObj.length() != 0) {
					for (int i = 0; i < array.length(); i++) {
						array.getJSONObject(i).put("project_testcase_id",
								testcaseIdObj.getString(array.getJSONObject(i).getString("testcase_name")));
						if (array.getJSONObject(i).has("test_step_number")) {
							array.getJSONObject(i).remove("test_step_number");
							array.getJSONObject(i).remove("test_step");
							array.getJSONObject(i).remove("sno");
							array.getJSONObject(i).remove("expected_result");

						}
					}
					jdbcTemplate.insert("TESECASE_MAPPER", array, con);
					response = "Success";
				}

			}
			con.commit();
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while insertOrUpdateAssigneTestCasesMapper");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while insertOrUpdateAssigneTestCasesMapper");
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while insertOrUpdateAssigneTestCasesMapper");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught while insertOrUpdateAssigneTestCasesMapper");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while insertOrUpdateAssigneTestCasesMapper");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while insertOrUpdateAssigneTestCasesMapper");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
				if (ps != null) {
					ps.close();
					ps = null;
				}
				if (rs != null) {
					rs.close();
					rs = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}

		return response;
	}

	/**
	 * tgetUploadTestSuiteData
	 * 
	 * @author Praveen
	 * 
	 * @created By Praveen on 07-02-2018
	 * 
	 * @modified by Praveen on 07-02-2018
	 * 
	 * @Description the getUploadTestSuiteData
	 * 
	 * @param String data
	 * 
	 * @return JSONArray
	 */
	public JSONArray getTestCaseMapperData(JSONObject inputfromUser) {
		Connection connForCustomisedUserData = null;
		JSONArray jArray = new JSONArray();
		try {
			String oderBy = inputfromUser.getString("order").equalsIgnoreCase("") ? ""
					: " ORDER BY " + inputfromUser.getString("order");
			int start = inputfromUser.getInt("start");
			int length = inputfromUser.getInt("length");
			String project_id = inputfromUser.getString("project_id");
			String testsuite_id = inputfromUser.getString("testsuite_id");
			String execution_id = inputfromUser.getString("scheduler_id");
			String assign = inputfromUser.getString("assign");
			String whereCondition = constructWhereCondition(inputfromUser.getJSONObject("customFilters"), assign,
					project_id, testsuite_id, execution_id);
			String limitQuery = "";
			if (length > 0) {
				limitQuery = " LIMIT " + start + "," + length;
			}
			String mainQuery = "Select (Select COUNT(*)  FROM `PROJECT_TESTCASES` WHERE `PROJECT_ID` = '" + project_id
					+ "'" + whereCondition
					+ ") AS recordsTotal, PROJECT_TESTCASES.* FROM `PROJECT_TESTCASES` WHERE `PROJECT_ID` = '"
					+ project_id + "'" + whereCondition + oderBy + limitQuery;
			connForCustomisedUserData = DatabaseConnection.getConnection();
			jArray = new JDBCTemplate().select(mainQuery, connForCustomisedUserData);
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getTestCaseMapperData");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getTestCaseMapperData");
		} finally {
			try {
				if (connForCustomisedUserData != null) {
					connForCustomisedUserData.close();
					connForCustomisedUserData = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}

		return jArray;

	}

	public JSONArray getTestCaseMapperData(String project_id, String testsuite_id, String scheduler_id, String assign,
			String start, String length, String column, String dir) {
		Connection con = null;
		JSONArray jArray = new JSONArray();
		try {
			con = DatabaseConnection.getConnection();
			// set auto commit to false
			String whereCondition = constructWhereCondition(new JSONObject(), assign, project_id, testsuite_id,
					scheduler_id);
			String limitQuery = "";
			limitQuery = " ORDER BY " + column + " LIMIT " + start + "," + length;
			String mainQuery = "Select * FROM `PROJECT_TESTCASES` WHERE `PROJECT_ID` = '" + project_id + "'"
					+ whereCondition + limitQuery;
			System.out.println("mainQuerymainQuery: " + mainQuery);
			con = DatabaseConnection.getConnection();
			jArray = new JDBCTemplate().select(mainQuery, con);
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getTestsuiteDetails");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getTestsuiteDetails");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return jArray;
	}

	private String constructWhereCondition(JSONObject jInputData, String assign, String project_id, String testsuite_id,
			String scheduler_id) {
		String filters = "";
		try {
			Iterator<?> keys = jInputData.keys();
			while (keys.hasNext()) {
				String key = (String) keys.next();
				JSONArray values = jInputData.getJSONArray(key);
				if (values.length() != 0)
					filters = filters + " AND `" + key + "` IN(" + constructString(values) + ")";
			}
			String inParameter = assign.equalsIgnoreCase("1") ? "IN" : "NOT IN";
			filters = filters + " AND `PROJECT_TESTCASE_ID` " + inParameter
					+ " (SELECT DISTINCT PROJECT_TESTCASE_ID FROM `TESECASE_MAPPER` " + "WHERE `PROJECT_ID` ='"
					+ project_id + "' AND `TESTSUITE_ID` = '" + testsuite_id + "' AND `SCHEDULER_ID`='" + scheduler_id
					+ "')";
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while constructWhereCondition");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while constructWhereCondition");
		}
		return filters;

	}

	private String constructString(JSONArray inputData) {
		String result = "";
		try {
			for (int i = 0; i < inputData.length(); i++) {
				if (i == 0) {
					result = "'" + inputData.getString(i) + "'";
				} else {
					result = result + ",'" + inputData.getString(i) + "'";
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while constructString");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while constructString");
		}
		return result;

	}

	public String deleteAssinedTestCases(JSONObject object) {
		String response = "99";
		Connection con = null;
		try {
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			con = DatabaseConnection.getConnection();
			con.setAutoCommit(false);
			if (object.length() != 0) {
				String query = "DELETE FROM `TESECASE_MAPPER` WHERE `PROJECT_ID`='" + object.getString("project_id")
						+ "' AND `TESTSUITE_ID` = '" + object.getString("testsuite_id") + "' AND `SCHEDULER_ID` ='"
						+ object.getString("scheduler_id") + "' AND `PROJECT_TESTCASE_ID` = '"
						+ object.getString("project_testcase_id") + "'";
				jdbcTemplate.delete(query, con);
				response = "00";

			}
			con.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while deleteUser");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught deleteUser");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while deleteUser");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while deleteUser");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}

		return response;
	}

	public String insertOrUpdateProjectTestDataHeader(JSONObject object, Connection con) {
		String response = "99";
		try {
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			JSONObject input_headerData = new JSONObject();
			JSONArray array = object.getJSONArray("header_data");
			String project_Id = object.getString("project_id");
			JSONObject db_headers = getTestDataHeaders(project_Id, con);
			if (db_headers.length() != 0) {
				JSONObject key_with_column = db_headers.getJSONObject("key_with_column");
				System.out.println(key_with_column.length() + " :key_with_column" + key_with_column);
				int max_col = key_with_column.length() - 1;
				String updateQuery = "";
				for (int i = 0; i < array.length(); i++) {
					boolean isExist = false;
					Iterator<String> keys = key_with_column.keys();
					while (keys.hasNext()) {
						String key = keys.next();
						String db_value = key_with_column.getString(key);
						// System.out.println("db_value" + db_value);
						// System.out.println("array.getString(i)" + array.getString(i));
						if (db_value.equalsIgnoreCase(array.getString(i).trim())) {
							isExist = true;
							break;
						}

					}
					System.out.println("isExist:" + isExist);
					if (!isExist) {
						if (!updateQuery.equalsIgnoreCase("")) {
							updateQuery = updateQuery + ", `" + max_col + "` = '" + array.getString(i) + "'";
						} else {
							updateQuery = " `" + max_col + "`='" + array.getString(i) + "'";
						}
						max_col = max_col + 1;
					}
				}
				if (!updateQuery.equalsIgnoreCase("")) {
					String query = "UPDATE `PROJECT_TESTDATA_HADERS` SET" + updateQuery + " WHERE `PROJECT_ID` ='"
							+ project_Id + "'";
					System.out.println("queryqueryquery: " + query);
					jdbcTemplate.updateByQuery(query, con);
					response = "00";
				}
			} else {
				input_headerData.put("project_id", project_Id);
				for (int i = 1; i <= array.length(); i++) {
					input_headerData.put(String.valueOf(i), array.getString(i - 1));
				}
				jdbcTemplate.insert("PROJECT_TESTDATA_HADERS", input_headerData, con);
				response = "00";
			}

		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while insertOrUpdateProjectTestDataHeader");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while insertOrUpdateProjectTestDataHeader");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while insertOrUpdateProjectTestDataHeader");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while insertOrUpdateProjectTestDataHeader");
		}

		return response;
	}

	/**
	 * this function insert table data with respect to columns
	 * 
	 * @author Praveen Kumar Reddy Rachala
	 * 
	 * @Created on 06-03-2019
	 * 
	 * @modified by Praveen Kumar Reddy Rachala
	 * 
	 * @description return integer 1 Succes: 2: Invalid Input: 0:ERROR
	 * 
	 * @param String     table Name
	 * 
	 * @param JSONObject JSON Array
	 */
	public JSONObject insertTestData(JSONObject inputObject, Connection con) {
		PreparedStatement ps = null;
		System.out.println("inputObjectinputObject" + inputObject);
		String result = "99";
		JSONObject JResult = new JSONObject();
		JSONArray existTestCasesArray = new JSONArray();
		JSONArray testCasenameNotExistArray = new JSONArray();
		try {
			if (inputObject.length() != 0) {
				String project_id = inputObject.getString("project_id");

				String existTestCases = "SELECT DISTINCT `TESTCASENAME`,`PROJECT_TESTCASE_ID` FROM `PROJECT_TESTCASES` WHERE `PROJECT_ID`='"
						+ project_id + "'";
				System.out.println(existTestCases);
				existTestCasesArray = new JDBCTemplate().select(existTestCases, con);
				System.out.println("inputObjecttestdat:" + inputObject);
				System.out.println();
				System.out.println();
				System.out.println();

				JSONArray array = inputObject.getJSONArray("test_data");
				for (int i = 0; i < array.length(); i++) {
					boolean isExist = false;
					JSONObject parent = array.getJSONObject(i);
					System.out.println("parent object:" + parent);
					for (int j = 0; j < existTestCasesArray.length(); j++) {
						JSONObject child = existTestCasesArray.getJSONObject(j);
						// System.out.println("Parent:"+parent.getString("Test_Case_Name"));
						System.out.println("Child:" + child.getString("testcasename"));

						if (parent.getString("Test_Case_Name").equalsIgnoreCase(child.getString("testcasename"))) {
							System.out.println("yes+" + child.getString("testcasename"));
							array.getJSONObject(i).put("project_testcase_id", child.getString("project_testcase_id"));
							array.getJSONObject(i).put("project_id", project_id);

							isExist = true;
							break;
						}

					}
					if (!isExist) {
						testCasenameNotExistArray.put(parent);
					}
				}
				if (testCasenameNotExistArray.length() == 0) {
					JSONObject headers = getTestDataHeaders(project_id, con);
					JSONObject key_with_column = headers.getJSONObject("key_with_column");
					JSONObject key_with_value = headers.getJSONObject("key_with_value");
					key_with_column.put("project_id", "project_id");
					key_with_column.put("project_testcase_id", "project_testcase_id");
					key_with_value.put("project_id", "project_id");
					key_with_value.put("project_testcase_id", "project_testcase_id");
					key_with_value.remove("project_testdata_header_id");
					key_with_column.remove("project_testdata_header_id");
					Iterator<String> keys = key_with_column.keys();
					String constructParameters = "";
					String constructQueryParameters = "";
					List<String> arrayParameters = new ArrayList<String>();
					while (keys.hasNext()) {
						String key = keys.next();
						if (!constructParameters.equalsIgnoreCase("")) {
							constructParameters = constructParameters + ", `" + key.toUpperCase() + "`";
							constructQueryParameters = constructQueryParameters + ", ?";
						} else {
							constructParameters = "`" + key.toUpperCase() + "`";
							constructQueryParameters = "?";
						}
						arrayParameters.add(key_with_column.getString(key));
					}

					String constructInsertQuery = "INSERT INTO `PROJECT_TESTDATA_VALUES` (" + constructParameters
							+ ") VALUES (" + constructQueryParameters + ")";

					if (log.isDebugEnabled()) {
						log.debug("insertConstructInsertQuery" + constructInsertQuery);

					}
					ps = con.prepareStatement(constructInsertQuery);
					int count = 0;
					final int batchSize = 1000;
					for (int i = 0; i < array.length(); i++) {
						JSONObject insertData = array.getJSONObject(i);
						for (int j = 0; j < arrayParameters.size(); j++) {
							if (insertData.has(arrayParameters.get(j))) {
								ps.setString(j + 1, insertData.getString(arrayParameters.get(j)));
							} else {
								ps.setString(j + 1, "");
							}

						}
						ps.addBatch();
						if (++count % batchSize == 0) {
							ps.executeBatch();
							count = 0;

						}
					}
					ps.executeBatch();
					result = "00";
				}
			}
			JResult.put("status", result);
			JResult.put("test_cases", testCasenameNotExistArray);
		} catch (SQLException se) {
			se.printStackTrace();
			log.error("SQL exception caught while insert method" + se.getMessage());
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"SQL exception caught while insert method");
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSON Exception caught while insert method");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while insert method");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while insert method");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while insert method");
		} finally {
			try {
				if (ps != null) {
					ps.close();
					ps = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return JResult;
	}

	/**
	 * this function returns table data with respect to columns
	 * 
	 * @author Praveen Kumar Reddy Rachala
	 * 
	 * @Created on 06-03-2019
	 * 
	 * @modified by Praveen Kumar Reddy Rachala
	 * 
	 * @description return table data as per given input table in array format
	 * 
	 * @param String     table Name
	 * 
	 * @param JSONObject JSON Object
	 */
	public JSONObject getTestData(JSONObject inputObject, Connection con) {
		PreparedStatement ps = null;
		ResultSet rs = null;
		JSONArray test_data = new JSONArray();
		JSONObject result = new JSONObject();
		try {
			String filter_by = inputObject.getString("filter_by");
			String project_id = inputObject.getString("project_id");
			JSONArray filter_data = inputObject.getJSONArray("filter_data");
			String inParameter = "";

			JSONObject header_data = getTestDataHeaders(project_id, con);
			if (header_data.length() != 0) {
				JSONObject key_with_column = header_data.getJSONObject("key_with_column");
				JSONObject key_with_value = header_data.getJSONObject("key_with_value");
				key_with_column.put("project_testdata_values_id", "project_testdata_values_id");
				key_with_column.put("project_id", "project_id");
				key_with_column.put("project_testcase_id", "project_testcase_id");
				result.put("headers", key_with_column);

				Iterator<String> keys = key_with_column.keys();
				String selectParameter = "";
				while (keys.hasNext()) {
					String key = keys.next();
					if (!key.equalsIgnoreCase("project_testdata_header_id")) {
						if (!selectParameter.equalsIgnoreCase("")) {
							selectParameter = selectParameter + ", `" + key.toUpperCase() + "`";
						} else {
							selectParameter = " `" + key.toUpperCase() + "`";
						}
					}
				}

				if (filter_data.length() != 0) {
					if (filter_by.equalsIgnoreCase("Test_Case_Name") && key_with_value.has("Test_Case_Name")) {
						filter_by = key_with_value.getString("Test_Case_Name");
					}
					for (int i = 0; i < filter_data.length(); i++) {
						if (!inParameter.equalsIgnoreCase(""))
							inParameter = inParameter + ",'" + filter_data.getString(i) + "'";
						else
							inParameter = "'" + filter_data.getString(i) + "'";
					}
					inParameter = " AND `" + filter_by.toUpperCase() + "` IN (" + inParameter + ")";
				}

				// String limitQuery = " ORDER BY `"+filter_by.toUpperCase()+"` "+dir+" LIMIT
				// "+start+","+length;
				String getTestDataquery = "SELECT" + selectParameter
						+ " FROM `PROJECT_TESTDATA_VALUES` WHERE `PROJECT_ID`='" + project_id + "'" + inParameter;
				if (log.isDebugEnabled()) {
					log.debug("getTestDataQuerrrrr" + getTestDataquery);
				}
				ps = con.prepareStatement(getTestDataquery);
				rs = ps.executeQuery(getTestDataquery);
				java.sql.ResultSetMetaData rsmd = rs.getMetaData();
				while (rs.next()) {
					JSONObject testDataObj = new JSONObject();
					int numColumns = rsmd.getColumnCount();
					for (int i = 1; i < numColumns + 1; i++) {
						String column_name = rsmd.getColumnName(i);
						String value = rs.getString(column_name);
						testDataObj.put(key_with_column.getString(column_name.toLowerCase()), value);

					}
					test_data.put(testDataObj);

				}
				key_with_column.remove("project_testdata_header_id");
				key_with_column.remove("project_testdata_values_id");
				key_with_column.remove("project_id");
				key_with_column.remove("project_testcase_id");
				result.put("status", "00");
				result.put("headers", key_with_column);
			} else {
				result.put("status", "99");
			}
			result.put("test_data", test_data);

		} catch (SQLException se) {
			se.printStackTrace();
			log.error("SQL exception caught while getTestData method" + se.getMessage());
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"SQL exception caught while getTestData method");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getTestData method");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getTestData method");
		} finally {
			try {
				if (ps != null) {
					ps.close();
					ps = null;
				}
				if (rs != null) {
					rs.close();
					rs = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return result;

	}

	/**
	 * this function returns table data with respect to columns
	 * 
	 * @author Praveen Kumar Reddy Rachala
	 * 
	 * @Created on 06-03-2019
	 * 
	 * @modified by Praveen Kumar Reddy Rachala
	 * 
	 * @description return table data as per given input table in array format
	 * 
	 * @param String     table Name
	 * 
	 * @param JSONObject JSON Object
	 */
	public JSONObject getTestDataHeaders(String project_Id, Connection con) {
		PreparedStatement ps = null;
		ResultSet rs = null;
		JSONObject result = new JSONObject();
		JSONObject key_with_column = new JSONObject();
		JSONObject key_with_value = new JSONObject();
		try {
			if (!project_Id.equalsIgnoreCase("")) {
				String testDataHeadersquery = "SELECT * FROM `PROJECT_TESTDATA_HADERS` WHERE `PROJECT_ID`='"
						+ project_Id + "'";
				if (log.isDebugEnabled()) {
					log.debug("get select test Data Headers query" + testDataHeadersquery);
				}
				ps = con.prepareStatement(testDataHeadersquery);
				rs = ps.executeQuery(testDataHeadersquery);
				java.sql.ResultSetMetaData rsmd = rs.getMetaData();
				while (rs.next()) {
					int numColumns = rsmd.getColumnCount();
					for (int i = 1; i < numColumns + 1; i++) {
						String column_name = rsmd.getColumnName(i);
						String value = rs.getString(column_name).trim();
						if (!value.equalsIgnoreCase("")) {
							key_with_column.put(column_name.toLowerCase(), rs.getString(column_name));
							key_with_value.put(rs.getString(column_name), column_name.toLowerCase());
						}

					}
				}
			}
			if (key_with_column.length() != 0) {
				result.put("key_with_column", key_with_column);
				result.put("key_with_value", key_with_value);
			}
		} catch (SQLException se) {
			se.printStackTrace();
			log.error("SQL exception caught while getTestDataHeaders method" + se.getMessage());
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"SQL exception caught while select method");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getTestDataHeaders method");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getTestDataHeaders method");
		} finally {
			try {
				if (ps != null) {
					ps.close();
					ps = null;
				}
				if (rs != null) {
					rs.close();
					rs = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return result;

	}

	public String executeTestSuiteData(JSONObject inputData) {
		Connection con = null;
		String status = "Invalid Data";
		try {
			JSONArray JData = inputData.getJSONArray("input_data");
			JSONArray testcases = new JSONArray();
			con = DatabaseConnection.getConnection();
			JDBCTemplate JDBCTemplate = new JDBCTemplate();
			// set auto commit to false
			con.setAutoCommit(false);
			for (int i = 0; i < JData.length(); i++) {
				JSONObject project = JData.getJSONObject(i);
				JSONArray testcasesTemp = project.getJSONArray("testcases");
				project.remove("testcases");
				String executionId = String.valueOf(JDBCTemplate.insert("EXECUTION_DETAILS", project, con));
				project.put("execution_id", executionId);
				testcases = constructexecutionTestcaseArray(project, testcasesTemp);
			}
			if (testcases.length() != 0) {
				JDBCTemplate.insert("EXECUTION_DETAILS_MAPPER", testcases, con);
			}
			con.commit();
			status = "Success";

		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while execute method" + e.getMessage());
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while execute method");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while execute method" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return status;

	}

	private JSONArray constructexecutionTestcaseArray(JSONObject project, JSONArray testcases) {

		JSONArray constructArray = new JSONArray();
		try {

			for (int i = 0; i < testcases.length(); i++) {

				JSONObject object = new JSONObject();
				JSONObject testcaseobject = testcases.getJSONObject(i);
				object.put("execution_id", project.getString("execution_id"));
				object.put("project_id", project.getString("project_id"));
				object.put("iteration_number", project.getString("iteration_number"));
				object.put("testsuite_id", project.getString("testsuite_id"));
				object.put("scheduler_id", project.getString("scheduler_id"));
				if (testcaseobject.has("project_testcase_id")) {
					object.put("testcase_id", testcaseobject.getString("project_testcase_id"));
				}
				object.put("testcase_name", testcaseobject.getString("testcasename"));
				object.put("testcase_data", testcases.getJSONObject(i).toString());

				constructArray.put(object);
			}
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("Exception caught while insert ExecutionDetails method" + e.getMessage());
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while insert ExecutionDetails method");
		}
		return constructArray;

	}

	public JSONArray getSchedularDetails(JSONObject object) {
		Connection con = null;
		JSONArray schedularInfo = new JSONArray();
		try {
			con = DatabaseConnection.getConnection();
			JDBCTemplate JDBCTemplate = new JDBCTemplate();
			// set auto commit to false
			con.setAutoCommit(false);
			String schedulerDeatilsQuery = "";

			JSONArray schedulerIdArray = object.getJSONArray("scheduler_ids");
			if (schedulerIdArray.length() != 0) {
				String inQuery = "";
				for (int i = 0; i < schedulerIdArray.length(); i++) {
					inQuery = schedulerIdArray.getInt(i) + ",";
				}
				inQuery = inQuery.substring(0, inQuery.length() - 1);

				schedulerDeatilsQuery = "SELECT * FROM `SCHEDULER` WHERE 1 and PROJECT_ID='"
						+ object.getString("project_id") + "' and SCHEDULER_ACTIVE_STATUS='1' and  SCHEDULER_ID IN ("
						+ inQuery + ")";
			} else {
				schedulerDeatilsQuery = "SELECT * FROM `SCHEDULER` WHERE 1 and PROJECT_ID='"
						+ object.getString("project_id") + "' and SCHEDULER_ACTIVE_STATUS='1'";

			}
			log.info("schedulerDeatilsQuery:" + schedulerDeatilsQuery);

			schedularInfo = JDBCTemplate.select(schedulerDeatilsQuery, con);
			// set auto commited to database
			con.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while getSchedular Details");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught  getSchedular Details");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getSchedular Details");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getSchedular Details");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return schedularInfo;
	}

	public int updateSchedulerDetails(JSONObject object) {

		JSONObject whereCondition = new JSONObject();
		JSONObject upDateObj = new JSONObject();
		JSONArray updateDataArray = new JSONArray();
		Connection con = DatabaseConnection.getConnection();
		try {
			whereCondition.put("scheduler_id", object.getString("scheduler_id"));
			upDateObj.put("filter_data", whereCondition);
			object.remove("scheduler_id");
			upDateObj.put("update_data", object);
			updateDataArray.put(upDateObj);
			con.setAutoCommit(false);
			int status = new JDBCTemplate().update("SCHEDULER", updateDataArray, con);
			con.commit();
			return status;
			// execution_mapper_id

		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while update SchedulerDetails");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while update SchedulerDetails");
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while update SchedulerDetails");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught while update SchedulerDetails");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while update SchedulerDetails");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while update SchedulerDetails");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}
	}

	public String deleteSchedulerById(String scheduler_id) {
		String response = "99";
		try {
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			Connection con = DatabaseConnection.getConnection();
			con.setAutoCommit(false);
			if (!scheduler_id.equalsIgnoreCase("")) {
				String query = "UPDATE `SCHEDULER` SET `SCHEDULER_ACTIVE_STATUS` = '0' WHERE `SCHEDULER_ID` ='"
						+ scheduler_id + "'";
				jdbcTemplate.updateByQuery(query, con);
				response = "00";

			}
			con.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while delete Scheduler");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught while delete Scheduler");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while delete Scheduler");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while delete Scheduler");
		}

		return response;
	}

	public JSONObject getDashBordData(JSONObject object) {
		Connection con = null;
		JSONObject dashBordInfo = new JSONObject();
		JSONArray testSuitArray = new JSONArray();
		int project_level_test_case_count = 0;
		int project_level_pass_test_case_count = 0;
		int project_level_fail_test_case_count = 0;
		try {
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			con = DatabaseConnection.getConnection();
			// set auto commit to false
			String quey = "";
			String project_id = object.getString("project_id");
			String from_date = object.getString("from_date");
			String to_date = object.getString("to_date");

			/*
			 * String whereCondition = " AND EX.`PROJECT_ID` ='" + project_id + "'"; if
			 * (!from_date.equalsIgnoreCase("")) { whereCondition = whereCondition +
			 * " AND EX.`EXECUTION_TIME` >= '" + from_date + " 00:00:00'"; } if
			 * (!to_date.equalsIgnoreCase("")) { whereCondition = whereCondition +
			 * " AND EX.`EXECUTION_TIME` <= '" + to_date + " 23:59:59'"; }
			 * 
			 * whereCondition = whereCondition + " ORDER BY EX.`EXECUTION_TIME` ASC";
			 */
			JSONArray testsuiteArray = object.getJSONArray("testsuite_name");

			String whereCondition = " AND EX.`PROJECT_ID` ='" + project_id
					+ "'  AND EM.TESTCASE_RESULT !='' AND EX.TESTSUITE_NAME!=''";
			if (!from_date.equalsIgnoreCase("")) {
				whereCondition = whereCondition + " AND EX.`EXECUTION_TIME` >= '" + from_date + " 00:00:00'";
			}
			if (!to_date.equalsIgnoreCase("")) {
				whereCondition = whereCondition + " AND EX.`EXECUTION_TIME` <= '" + to_date + " 23:59:59'";
			}
			if (testsuiteArray.length() != 0) {
				StringBuffer testsuite = new StringBuffer();
				for (int i = 0; i < testsuiteArray.length(); i++) {
					testsuite.append("'" + testsuiteArray.getString(i) + "',");
				}
				String testsuie_name = testsuite.toString();
				testsuie_name = testsuie_name.substring(0, testsuie_name.length() - 1);
				whereCondition = whereCondition + " AND EX.TESTSUITE_NAME IN(" + testsuie_name
						+ ") ORDER BY EX.`EXECUTION_TIME` ASC";
			} else {
				whereCondition = whereCondition + " ORDER BY EX.`EXECUTION_TIME` ASC";
			}
			quey = "SELECT EX.`TESTSUITE_NAME`, EX.`TESTSUITE_ID`, EX.`EXECUTION_TIME`, EX.`EXECUTION_ID`, EX.`EXECUTION_ID`,"
					+ " EM.`EXECUTION_MAPPER_ID`, EM.`TESTCASE_ID`, EM.`TESTCASE_NAME`,"
					+ " EM.`TESTCASE_RESULT`,EX.`ITERATION_NUMBER`,EM.`TESTCASE_DURATION`  FROM `EXECUTION_DETAILS` AS EX, `EXECUTION_DETAILS_MAPPER` AS EM WHERE EX.`EXECUTION_ID` = EM.`EXECUTION_ID` AND EX.`PROJECT_ID` = EM.`PROJECT_ID`"
					+ whereCondition;
			System.out.println("quey:" + quey);
			JSONArray excutionData = jdbcTemplate.select(quey, con);
			JSONObject testSuitLevelData = new JSONObject();
			JSONObject excuitionLevelData = new JSONObject();
			JSONArray OrderOfTestSuitLevelData = new JSONArray();
			JSONArray OrderOfExcuitionLevelData = new JSONArray();
			for (int i = 0; i < excutionData.length(); i++) {
				JSONObject excutionObj = excutionData.getJSONObject(i);
				String testsuite_id = excutionObj.getString("testsuite_id");
				String testcase_result = excutionObj.getString("testcase_result");
				String execution_id = excutionObj.getString("execution_id");

				int pass = 0;
				int fail = 0;
				if (testcase_result.equalsIgnoreCase("PASSED")) {
					pass = 1;
				} else if (testcase_result.equalsIgnoreCase("Failed")) {
					fail = 1;
				}

				project_level_test_case_count = project_level_test_case_count + 1;
				project_level_pass_test_case_count = project_level_pass_test_case_count + pass;
				project_level_fail_test_case_count = project_level_fail_test_case_count + fail;
				JSONObject testSuiteObj = null;
				if (!testSuitLevelData.has(testsuite_id)) {
					testSuiteObj = new JSONObject();
					testSuiteObj.put("testsuite_id", excutionObj.getString("testsuite_id"));
					testSuiteObj.put("total_testcase_count", String.valueOf(1));
					testSuiteObj.put("passed_testcase_count", String.valueOf(pass));
					testSuiteObj.put("failed_testcase_count", String.valueOf(fail));
					testSuiteObj.put("testsuite_name", excutionObj.getString("testsuite_name"));

					testSuiteObj.put("last_execution_time", excutionObj.getString("execution_time"));
					testSuiteObj.put("iteration_count", "0");
					testSuitLevelData.put(testsuite_id, testSuiteObj);
					OrderOfTestSuitLevelData.put(testsuite_id);
				} else {
					testSuiteObj = testSuitLevelData.getJSONObject(testsuite_id);
					testSuiteObj.put("total_testcase_count",
							String.valueOf(Integer.parseInt(testSuiteObj.getString("total_testcase_count")) + 1));
					testSuiteObj.put("passed_testcase_count",
							String.valueOf(Integer.parseInt(testSuiteObj.getString("passed_testcase_count")) + pass));
					testSuiteObj.put("failed_testcase_count",
							String.valueOf(Integer.parseInt(testSuiteObj.getString("failed_testcase_count")) + fail));
					testSuiteObj.put("iteration_count", "0");

				}
				testSuiteObj.put("last_iterarion", excutionObj.getString("iteration_number"));

				JSONObject excution_obj = null;
				JSONArray testDataCaseArray = null;
				if (!excuitionLevelData.has(execution_id)) {
					excution_obj = new JSONObject();
					testDataCaseArray = new JSONArray();
					excution_obj.put("testsuite_id", excutionObj.getString("testsuite_id"));
					excution_obj.put("testsuite_id", excutionObj.getString("testsuite_id"));
					// excution_obj.put("iteration_number",
					// excutionObj.getString("iteration_number"));
					excution_obj.put("iteration_number", excutionObj.getString("iteration_number"));

					excution_obj.put("execution_time", excutionObj.getString("execution_time"));
					excution_obj.put("total_testcase_count", String.valueOf(1));
					excution_obj.put("passed_testcase_count", String.valueOf(pass));
					excution_obj.put("failed_testcase_count", String.valueOf(fail));

					/* Code Changed by Pavan for ExecuitonId */
					/* Start */

					excution_obj.put("execution_id", execution_id);

					/* End */

					JSONObject testCaseData = new JSONObject();
					testCaseData.put("execution_mapper_id", excutionObj.getString("execution_mapper_id"));
					testCaseData.put("testsuite_id", excutionObj.getString("testsuite_id"));
					testCaseData.put("testcase_name", excutionObj.getString("testcase_name"));
					testCaseData.put("testcase_result", excutionObj.getString("testcase_result"));
					testCaseData.put("iteration_number", excutionObj.getString("iteration_number"));
					testCaseData.put("testcase_duration", excutionObj.getString("testcase_duration"));
					testDataCaseArray.put(testCaseData);
					excution_obj.put("testcasedata", testDataCaseArray);
					OrderOfExcuitionLevelData.put(execution_id);
					excuitionLevelData.put(execution_id, excution_obj);
				} else {
					excution_obj = excuitionLevelData.getJSONObject(execution_id);
					excution_obj.put("total_testcase_count",
							String.valueOf(Integer.parseInt(excution_obj.getString("total_testcase_count")) + 1));
					excution_obj.put("passed_testcase_count",
							String.valueOf(Integer.parseInt(excution_obj.getString("passed_testcase_count")) + pass));
					excution_obj.put("failed_testcase_count",
							String.valueOf(Integer.parseInt(excution_obj.getString("failed_testcase_count")) + fail));
					excution_obj.put("iteration_count", "0");

					JSONObject testCaseData = new JSONObject();
					testCaseData.put("execution_mapper_id", excutionObj.getString("execution_mapper_id"));
					testCaseData.put("testsuite_id", excutionObj.getString("testsuite_id"));
					testCaseData.put("testcase_name", excutionObj.getString("testcase_name"));
					testCaseData.put("testcase_result", excutionObj.getString("testcase_result"));
					testCaseData.put("iteration_number", excutionObj.getString("iteration_number"));
					testCaseData.put("testcase_duration", excutionObj.getString("testcase_duration"));

					excution_obj.put("testcasedata", excution_obj.getJSONArray("testcasedata").put(testCaseData));
				}
			}
			for (int i = 0; i < OrderOfTestSuitLevelData.length(); i++) {
				String last_iterarion_total_testcase_count = "";
				String last_iterarion_passed_testcase_count = "";
				String last_iterarion_failed_testcase_count = "";
				String testSuitId = OrderOfTestSuitLevelData.getString(i);
				JSONObject testSuitObj = testSuitLevelData.getJSONObject(testSuitId);
				JSONArray excuitionArray = new JSONArray();
				for (int j = 0; j < OrderOfExcuitionLevelData.length(); j++) {
					String excuitionId = OrderOfExcuitionLevelData.getString(j);
					JSONObject excuitionObj = excuitionLevelData.getJSONObject(excuitionId);
					if (excuitionObj.getString("testsuite_id").equalsIgnoreCase(testSuitId)) {

						last_iterarion_total_testcase_count = excuitionObj.getString("total_testcase_count");
						last_iterarion_passed_testcase_count = excuitionObj.getString("passed_testcase_count");
						last_iterarion_failed_testcase_count = excuitionObj.getString("failed_testcase_count");

						excuitionArray.put(excuitionObj);
					}
				}
				testSuitObj.put("execution_data", excuitionArray);
				testSuitObj.put("last_iterarion_total_testcase_count", last_iterarion_total_testcase_count);
				testSuitObj.put("last_iterarion_passed_testcase_count", last_iterarion_passed_testcase_count);
				testSuitObj.put("last_iterarion_failed_testcase_count", last_iterarion_failed_testcase_count);
				testSuitObj.put("total_iteration_count", String.valueOf(excuitionArray.length()));

				testSuitArray.put(testSuitObj);
			}

//			for(int i=0;i<testSuitArray.length();i++)
//			{
//				JSONObject testsuiteObj=testSuitArray.getJSONObject(i);
//				String testsuite_id=testsuiteObj.getString("testsuite_id");
//				JSONArray execution_array=testsuiteObj.getJSONArray("execution_data");
//				JSONObject exobj=execution_array.getJSONObject(execution_array.length()-1);
//				testsuiteObj.put("last_iterarion_total_testcase_count", exobj.getString("total_testcase_count"));
//				testsuiteObj.put("last_iterarion_passed_testcase_count",exobj.getString("passed_testcase_count"));
//				testsuiteObj.put("last_iterarion_failed_testcase_count",exobj.getString("failed_testcase_count"));
//				testSuitArray.put(i,testsuiteObj);
//			}
			dashBordInfo.put("testsuit_data", testSuitArray);
			dashBordInfo.put("total_test_suits", testSuitArray.length());
			dashBordInfo.put("total_testcases", project_level_test_case_count);
			dashBordInfo.put("pass_test_case_count", project_level_pass_test_case_count);
			dashBordInfo.put("fail_test_case_count", project_level_fail_test_case_count);
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("Exception caught while getDashBordData ");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getSchedular Details");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getDashBordData ");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getSchedular Details");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return dashBordInfo;
	}

	public String convertDBDateFormate(String date) {
		String currentTime = "";
		try {
			Date dt = new Date(date);

			java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

			currentTime = sdf.format(dt);
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while convertDBDateFormate");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while convertDBDateFormate");
		}
		return currentTime;
	}

	public String insertRecentActivites(String message, String userId, Connection con) {
		String response = "";
		try {
			JSONObject input = new JSONObject();
			input.put("login_user_id", userId);
			input.put("recent_activity", message);
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			jdbcTemplate.insert("RECENT_ACTIVITES", input, con);
			response = "Success";
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while insertOrUpdateAssigneTestCasesMapper");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while insertOrUpdateAssigneTestCasesMapper");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while insertOrUpdateAssigneTestCasesMapper");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while insertOrUpdateAssigneTestCasesMapper");
		}

		return response;
	}

	public JSONObject getDashBordUserSpecificData(String userid, String project_id) {
		Connection con = null;
		JSONArray recentActivityArray = new JSONArray();
		JSONArray testsuitData = new JSONArray();
		JSONArray assigne_users = new JSONArray();
		JSONObject dashBordUserSpecificData = new JSONObject();
		try {
			JDBCTemplate jDBCTemplate = new JDBCTemplate();
			con = DatabaseConnection.getConnection();
			con = DatabaseConnection.getConnection();
			String recentActivityQuery = "SELECT R.`RECENT_ACTIVITES_TIME`, R.`LOGIN_USER_ID`,R.`RECENT_ACTIVITY`, U.`USERNAME` FROM `RECENT_ACTIVITES` AS R,`USER_DETAILS` AS U WHERE "
					+ "R.`LOGIN_USER_ID`='" + userid + "' AND R.`LOGIN_USER_ID`= U.`USER_ID` "
					+ "ORDER BY R.`RECENT_ACTIVITES_TIME` ASC";
			recentActivityArray = jDBCTemplate.select(recentActivityQuery, con);

			String testSuiteQuery = "SELECT TD.`TESTSUITE_ID`, TD.`TESTSUITE_NAME`, U.`USER_ID`, U.`USERNAME` "
					+ "FROM `TESTSUITE_DETAILS` AS TD, `USER_DETAILS` AS U WHERE TD.`USER_ID`= U.`USER_ID` AND TD.`TESTSUITE_STATUS` ='1' "
					+ "AND TD.`PROJECT_ID`='" + project_id + "' ORDER BY TD.`TESTSUITE_DETAILS_UPDATEDTIME` ASC";
			testsuitData = jDBCTemplate.select(testSuiteQuery, con);
			dashBordUserSpecificData.put("recentActivity", recentActivityArray);
			for (int i = 0; i < testsuitData.length(); i++) {
				JSONObject jdata = testsuitData.getJSONObject(i);
				String testSuitId = jdata.getString("testsuite_id");
				String testcaseMapperQuery = "SELECT count(*) AS count FROM `TESECASE_MAPPER` WHERE `PROJECT_ID`='"
						+ project_id + "' AND `TESTSUITE_ID` ='" + testSuitId + "' AND `SCHEDULER_ID` =''";
				String testcasecount = jDBCTemplate.select(testcaseMapperQuery, con).getJSONObject(0)
						.getString("count");
				testsuitData.getJSONObject(i).put("testcasecount", testcasecount);

			}
			dashBordUserSpecificData.put("testsuite", testsuitData);

			String project_assigne_users = "SELECT `USER_ID`,`USERNAME`,`ADMIN_TYPE` FROM `USER_DETAILS` WHERE `USER_ID` IN "
					+ "(SELECT DISTINCT `USER_ID` FROM `PROJECT_ASSIGN_USER` WHERE `PROJECT_ID` ='" + project_id + "' "
					+ "AND `TESTSUITE_ID`='') ORDER BY `USERNAME` ASC";
			assigne_users = jDBCTemplate.select(project_assigne_users, con);
			dashBordUserSpecificData.put("assigne_users", assigne_users);
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getRecentActivites");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getRecentActivites");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return dashBordUserSpecificData;
	}

	public int insertexecuteTestSuiteData(JSONObject inputData,Connection con) {
		int execution_id = 0;
		try {
			JSONArray JData = inputData.getJSONArray("input_data");
			JSONArray testcases = new JSONArray();
			JDBCTemplate JDBCTemplate = new JDBCTemplate();
			for (int i = 0; i < JData.length(); i++) {
				JSONObject project = JData.getJSONObject(i);
				JSONArray testcasesTemp = project.getJSONArray("testcases");
				String temperary_run = project.getString("temperary_run");
				if (project.has("manual_execution")) {
					project.remove("manual_execution");
					
				}

				project.remove("testcases");
				int iternumber = 1;

				if (!temperary_run.equalsIgnoreCase("1")) {
					String query = "SELECT MAX(ITERATION_NUMBER) as ITERATION_NUMBER FROM `EXECUTION_DETAILS` where PROJECT_ID='"
							+ project.getString("project_id") + "' and TESTSUITE_ID='"
							+ project.getString("testsuite_id") + "'";

					JSONArray array = JDBCTemplate.select(query, con);
					if (array.length() != 0) {
						JSONObject itrobj = array.getJSONObject(0);
						if (itrobj.has("iteration_number")) {
							iternumber = Integer.parseInt(itrobj.getString("iteration_number").trim());
							iternumber = iternumber + 1;
							project.put("iteration_number", iternumber + "");
						}

					}
				} else {
					project.put("iteration_number", 1 + "");
				}
				String executionId = String.valueOf(JDBCTemplate.insert("EXECUTION_DETAILS", project, con));
				System.out.println("executionId:" + executionId);
				execution_id = Integer.parseInt(executionId);
				project.put("execution_id", executionId);
				project.put("iteration_number", iternumber + "");
				project.put("temperary_run", temperary_run);
				testcases = constructexecutionTestcaseArray(project, testcasesTemp);

				if (testcases.length() != 0) {
					JDBCTemplate.insert("EXECUTION_DETAILS_MAPPER", testcases, con);
				} else {
					return -1;
				}
				String updateqry = "UPDATE `TARGET_REMOTE_MACHINES` SET `EXECUTION_ID`='" + execution_id
						+ "',`TARGET_REMOTE_MACHINE_STATUS`='1' where `TARGET_REMOTE_MACHINE_ID`='"
						+ project.getString("target_remote_machine_id") + "'";
				JDBCTemplate.updateByQuery(updateqry, con);
				project.put("testcases", testcasesTemp);
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while execute method" + e.getMessage());
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while execute method");
		} 
		return execution_id;

	}

	public String updateTestSuiteResults(JSONObject mainobject) {

		Connection con = DatabaseConnection.getConnection();
		String status = "InValidData";
		try {
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			con.setAutoCommit(false);
			JSONArray testcasearArray = mainobject.getJSONArray("testcase_data");
			/*
			 * for (int i = 0; i < testcasearArray.length(); i++) { JSONObject testcaseObj =
			 * testcasearArray.getJSONObject(i); String updateqry =
			 * "UPDATE `EXECUTION_DETAILS_MAPPER` SET `TESTCASE_RESULT` = '"+testcaseObj.
			 * getString("testcase_result")
			 * +"', `TESTCASE_DURATION` = TIMESTAMPDIFF(MINUTE,'"+testcaseObj.getString(
			 * "start_date")+"', NOW()) " +
			 * "WHERE `EXECUTION_MAPPER_ID`='"+testcaseObj.getString("execution_mapper_id")+
			 * "'"; jdbcTemplate.updateByQuery(updateqry, con); }
			 */
			for (int i = 0; i < testcasearArray.length(); i++) {
				JSONObject testcaseObj = testcasearArray.getJSONObject(i);
				String updateqry = "UPDATE `EXECUTION_DETAILS_MAPPER` SET `TESTCASE_RESULT` = '"
						+ testcaseObj.getString("testcase_result") + "', `TESTCASE_DURATION` = TIMESTAMPDIFF(SECOND,'"
						+ testcaseObj.getString("start_date") + "', NOW()) " + "WHERE `EXECUTION_MAPPER_ID`='"
						+ testcaseObj.getString("execution_mapper_id") + "'";
				jdbcTemplate.updateByQuery(updateqry, con);
			}
			con.commit();
			status = "Success";
			return status;
			// execution_mapper_id

		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while updateTestSuiteResults");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while update SchedulerDetails");
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while updateTestSuiteResults");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught while update SchedulerDetails");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while updateTestSuiteResults");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while update TestSuite Results");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}
	}

	public int executeTheScriptMachine(String execution_id) {

		Connection connection = DatabaseConnection1.getConnection();
		try {
			String selectqry = "SELECT  `REMOTE_MACHINE_IP`, `REMOTE_MACHINE_USERNAME`, `REMOTE_MACHINE_PASSWORD`, `TARGET_REMOTE_MACHINE_PROJECT_PATH`, `TARGET_REMOTE_MACHINE_OS`, `TARGET_REMOTE_MACHINE_SCRIPT_VERSION` FROM `EXECUTION_DETAILS` WHERE `EXECUTION_ID`='"
					+ execution_id + "'";
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			JSONArray array = jdbcTemplate.select(selectqry, connection);
			JSONObject scriptdetailsobj = array.getJSONObject(0);
			String url = "http://" + scriptdetailsobj.getString("remote_machine_ip")
					+ ":8080/TSE_WS/api/web/info/runbatchFile.json";
			URL obj = new URL(url);
			System.out.println("url" + url);

			HttpURLConnection con = (HttpURLConnection) obj.openConnection();
			con.setRequestMethod("POST");
			con.addRequestProperty("content-type", "application/json");
			String postParameters = scriptdetailsobj.toString();
			System.out.println("postParameters:" + postParameters);
			con.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes(postParameters);
			wr.flush();
			int responseCode = con.getResponseCode();
			return responseCode;
		} catch (MalformedURLException e) {
			e.printStackTrace();
			log.error("MalformedURLException caught while Executing Script");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"MalformedURLException caught while Executing Script");
		} catch (IOException e) {
			e.printStackTrace();
			log.error("IOException caught while Executing Script");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"IOException caught while Executing Script");
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while Executing Script");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while Executing Script");
		} finally {
			try {
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while execute method" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
	}

	public JSONObject getexecutingTestSuiteDetails(JSONObject object) {

		JSONArray jsonArray = new JSONArray();
		Connection connection = DatabaseConnection.getConnection();
		JSONObject mainObject = new JSONObject();

		try {
			String query = "SELECT EX.`PROJECT_ID`,EX.`PROJECT_NAME`,EX.`EXECUTION_OVERALLSTATUS`,EX.`TOTAL_TESTCASE_COUNT`,EX.`PASSED_TESTCASE_COUNT`,EX.`FAILED_TESTCASE_COUNT`,EX.`SKIPPED_TESTCASE_COUNT`,EX.`TESTSUITE_NAME`,EX.`TESTSUITE_ID`,EX.`EXECUTION_ID`,EM.`EXECUTION_MAPPER_ID`,EM.`TESTCASE_ID`,EM.`TESTCASE_NAME`,EM. `TESTCASE_DATA`,EM.`TESTCASE_RESULT`,EM.`SCHEDULER_ID` FROM `EXECUTION_DETAILS` AS EX,`EXECUTION_DETAILS_MAPPER` AS EM WHERE EX.`EXECUTION_ID`=EM.`EXECUTION_ID` AND EX.`EXECUTION_ID` IN (SELECT `EXECUTION_ID` FROM `TARGET_REMOTE_MACHINES` WHERE `TARGET_REMOTE_MACHINE_PROJECT_PATH`='"
					+ object.getString("target_remote_machine_project_path").replace("\\", "\\\\")
					+ "' and `REMOTE_MACHINE_IP`='" + object.getString("remote_machine_ip") + "')";
			System.out.println("query::" + query);
			jsonArray = new JDBCTemplate().select(query, connection);
			JSONArray temparArray = new JSONArray();
			if (jsonArray.length() != 0) {

				for (int i = 0; i < jsonArray.length(); i++) {
					JSONObject updateObj = jsonArray.getJSONObject(i);
					mainObject.put("skipped_testcase_count", "");
					mainObject.put("failed_testcase_count", "");
					mainObject.put("passed_testcase_count", "");
					mainObject.put("execution_overallstatus", "");
					mainObject.put("project_name", updateObj.getString("project_name"));
					mainObject.put("project_id", updateObj.getString("project_id"));
					mainObject.put("testsuite_id", updateObj.getString("testsuite_id"));
					mainObject.put("testsuite_name", updateObj.getString("testsuite_name"));
					mainObject.put("execution_id", updateObj.getString("execution_id"));
					JSONObject dummyobj = new JSONObject(updateObj.getString("testcase_data"));
					dummyobj.put("execution_mapper_id", updateObj.getString("execution_mapper_id"));
					temparArray.put(dummyobj);
				}
				mainObject.put("testcase_data", temparArray);

			}

		} catch (JSONException e) {
			e.printStackTrace();
			log.error("Exception caught while getexecutingTestSuiteDetails");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getexecutingTestSuiteDetails");
		} finally {
			try {
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}

		return mainObject;

	}

	public JSONArray getIterationsBasedOnTestSuiteIdInfo(String projectId, String testsuiteid, String start,
			String length, String column, String dir, String execution_id, String status, String start_date,
			String end_date) {
		Connection con = null;
		JSONArray projectInfo = new JSONArray();
		try {
			con = DatabaseConnection.getConnection();
			JDBCTemplate JDBCTemplate = new JDBCTemplate();
			// set auto commit to false
			con.setAutoCommit(false);
			String executionDetailsqry = "";
			String whereCondition = "";
			if (!start_date.equalsIgnoreCase("")) {
				whereCondition = whereCondition + " AND `EXECUTION_TIME` >= '" + start_date + " 00:00:00'";
			}
			if (!end_date.equalsIgnoreCase("")) {
				whereCondition = whereCondition + " AND `EXECUTION_TIME` <= '" + end_date + " 23:59:59'";
			}

			if ((execution_id == null || execution_id.equalsIgnoreCase(""))
					&& (status == null || status.equalsIgnoreCase(""))) {
				executionDetailsqry = "SELECT (SELECT COUNT(*) from `EXECUTION_DETAILS` WHERE `PROJECT_ID`='"
						+ projectId + "' and `TESTSUITE_ID`='" + testsuiteid + "'" + whereCondition
						+ ") as count,`EXECUTION_TIME`,`ITERATION_NUMBER`,`EXECUTION_ID` FROM `EXECUTION_DETAILS` WHERE `PROJECT_ID`='"
						+ projectId + "' and `TESTSUITE_ID`='" + testsuiteid + "'" + whereCondition + " ORDER BY `"
						+ column + "` " + dir + " LIMIT " + start + "," + length;
				projectInfo = JDBCTemplate.select(executionDetailsqry, con);

			} else if ((execution_id != null || !execution_id.equalsIgnoreCase(""))
					&& (status == null || status.equalsIgnoreCase(""))) {
				executionDetailsqry = "SELECT `EXECUTION_TIME`,`ITERATION_NUMBER`,`EXECUTION_ID` FROM `EXECUTION_DETAILS` WHERE `PROJECT_ID`='"
						+ projectId + "' and `TESTSUITE_ID`='" + testsuiteid + "' and EXECUTION_ID='" + execution_id
						+ "' ORDER BY `" + column + "` " + dir + " LIMIT " + start + "," + length;
				JSONArray array = JDBCTemplate.select(executionDetailsqry, con);
				String[] results = { "Passed", "Failed", "Skipped" };

				for (int i = 0; i < array.length(); i++) {
					JSONObject object = array.getJSONObject(i);
					int total_testcase_count = 0;
					for (String result : results) {
						if (result.equals("Passed")) {
							String qry = "SELECT `TESTCASE_RESULT` FROM `EXECUTION_DETAILS_MAPPER`  where `PROJECT_ID` = '"
									+ projectId + "' and `TESTSUITE_ID` = '" + testsuiteid + "' and `EXECUTION_ID` = '"
									+ execution_id + "' and `TESTCASE_RESULT` = '" + result + "'";
							JSONArray resultsarray1 = JDBCTemplate.select(qry, con);
							object.put("passed_testcase_count", resultsarray1.length());
							total_testcase_count = total_testcase_count + resultsarray1.length();
						} else if (result.equals("Failed")) {
							String qry = "SELECT `TESTCASE_RESULT` FROM `EXECUTION_DETAILS_MAPPER`  where `PROJECT_ID` = '"
									+ projectId + "' and `TESTSUITE_ID` = '" + testsuiteid + "' and `EXECUTION_ID` = '"
									+ execution_id + "' and `TESTCASE_RESULT` = '" + result + "'";
							JSONArray resultsarray = JDBCTemplate.select(qry, con);

							object.put("failed_testcase_count", resultsarray.length());
							total_testcase_count = total_testcase_count + resultsarray.length();
						} else {
							String qry = "SELECT `TESTCASE_RESULT` FROM `EXECUTION_DETAILS_MAPPER`  where `PROJECT_ID` = '"
									+ projectId + "' and `TESTSUITE_ID` = '" + testsuiteid + "' and `EXECUTION_ID` = '"
									+ execution_id + "' and `TESTCASE_RESULT` = '" + result + "'";
							JSONArray resultsarray = JDBCTemplate.select(qry, con);

							object.put("skipped_testcase_count", resultsarray.length());
							total_testcase_count = total_testcase_count + resultsarray.length();
						}

					}
					object.put("total_testcase_count", total_testcase_count);
					projectInfo.put(object);
				}

			} else if ((execution_id != null && !execution_id.equalsIgnoreCase(""))
					&& (status != null && !status.equalsIgnoreCase(""))) {
				executionDetailsqry = "SELECT `EXECUTION_MAPPER_ID`,`TESTCASE_NAME`,`TESTCASE_DESCRIPTION` FROM `EXECUTION_DETAILS_MAPPER` WHERE `PROJECT_ID`='"
						+ projectId + "' and `TESTSUITE_ID`='" + testsuiteid + "' and EXECUTION_ID='" + execution_id
						+ "' and `TESTCASE_RESULT`='" + status + "' ORDER BY `" + column + "` " + dir + " LIMIT "
						+ start + "," + length;
				projectInfo = JDBCTemplate.select(executionDetailsqry, con);

			}

			// set auto commited to database
			con.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while getIterationsBasedOnTestSuiteIdInfo");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught getIterationsBasedOnTestSuiteIdInfo");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getIterationsBasedOnTestSuiteIdInfo");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getIterationsBasedOnTestSuiteIdInfo");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return projectInfo;
	}

	/*
	 * public JSONObject getIterationsBasedOnTestSuiteIdInfo(String projectId,
	 * String testsuiteid, String start, String length, String column, String dir,
	 * String execution_id, String status) { Connection con = null; JSONArray
	 * projectInfo = new JSONArray(); JSONArray countwithprojectInfoArray=new
	 * JSONArray(); JSONObject countobj=new JSONObject();
	 * 
	 * try { con = DatabaseConnection.getConnection(); JDBCTemplate JDBCTemplate =
	 * new JDBCTemplate(); // set auto commit to false con.setAutoCommit(false);
	 * String executionDetailsqry = ""; String executionDetailscountqry=""; if
	 * ((execution_id == null || execution_id.equalsIgnoreCase("")) && (status ==
	 * null || status.equalsIgnoreCase(""))) { System.out.println("Condition1");
	 * executionDetailsqry =
	 * "SELECT `EXECUTION_TIME`,`ITERATION_NUMBER`,`EXECUTION_ID` FROM `EXECUTION_DETAILS` WHERE `PROJECT_ID`='"
	 * + projectId + "' and `TESTSUITE_ID`='" + testsuiteid + "' ORDER BY `" +
	 * column + "` " + dir + " LIMIT " + start + "," + length; projectInfo =
	 * JDBCTemplate.select(executionDetailsqry, con);
	 * executionDetailscountqry="SELECT COUNT(*) as COUNT FROM `EXECUTION_DETAILS` WHERE `PROJECT_ID`='"
	 * + projectId + "' and `TESTSUITE_ID`='" + testsuiteid+"'";
	 * System.out.println("executionDetailscountqry:"+executionDetailscountqry);
	 * JSONArray array=JDBCTemplate.select(executionDetailscountqry, con);
	 * countobj.put("count",array.getJSONObject(0).getString("count"));
	 * countobj.put("data",projectInfo);
	 * 
	 * 
	 * 
	 * 
	 * } else if ((execution_id != null || !execution_id.equalsIgnoreCase("")) &&
	 * (status == null || status.equalsIgnoreCase(""))) {
	 * System.out.println("Condition2");
	 * 
	 * executionDetailsqry =
	 * "SELECT `EXECUTION_TIME`,`ITERATION_NUMBER`,`EXECUTION_ID` FROM `EXECUTION_DETAILS` WHERE `PROJECT_ID`='"
	 * + projectId + "' and `TESTSUITE_ID`='" + testsuiteid + "' and EXECUTION_ID='"
	 * + execution_id + "' ORDER BY `" + column + "` " + dir + " LIMIT " + start +
	 * "," + length; JSONArray array = JDBCTemplate.select(executionDetailsqry,
	 * con); String[] results = { "Passed", "Failed", "Skipped" };
	 * 
	 * for (int i = 0; i < array.length(); i++) { JSONObject object =
	 * array.getJSONObject(i); int total_testcase_count = 0; for (String result :
	 * results) { if (result.equals("Passed")) { String qry =
	 * "SELECT `TESTCASE_RESULT` FROM `EXECUTION_DETAILS_MAPPER`  where `PROJECT_ID` = '"
	 * + projectId + "' and `TESTSUITE_ID` = '" + testsuiteid +
	 * "' and `EXECUTION_ID` = '" + execution_id + "' and `TESTCASE_RESULT` = '" +
	 * result + "'"; JSONArray resultsarray1 = JDBCTemplate.select(qry, con);
	 * object.put("passed_testcase_count", resultsarray1.length());
	 * total_testcase_count = total_testcase_count + resultsarray1.length(); } else
	 * if (result.equals("Failed")) { String qry =
	 * "SELECT `TESTCASE_RESULT` FROM `EXECUTION_DETAILS_MAPPER`  where `PROJECT_ID` = '"
	 * + projectId + "' and `TESTSUITE_ID` = '" + testsuiteid +
	 * "' and `EXECUTION_ID` = '" + execution_id + "' and `TESTCASE_RESULT` = '" +
	 * result + "'"; JSONArray resultsarray = JDBCTemplate.select(qry, con);
	 * 
	 * object.put("failed_testcase_count", resultsarray.length());
	 * total_testcase_count = total_testcase_count + resultsarray.length(); } else {
	 * String qry =
	 * "SELECT `TESTCASE_RESULT` FROM `EXECUTION_DETAILS_MAPPER`  where `PROJECT_ID` = '"
	 * + projectId + "' and `TESTSUITE_ID` = '" + testsuiteid +
	 * "' and `EXECUTION_ID` = '" + execution_id + "' and `TESTCASE_RESULT` = '" +
	 * result + "'"; JSONArray resultsarray = JDBCTemplate.select(qry, con);
	 * 
	 * object.put("skipped_testcase_count", resultsarray.length());
	 * total_testcase_count = total_testcase_count + resultsarray.length(); }
	 * 
	 * } object.put("total_testcase_count", total_testcase_count);
	 * projectInfo.put(object); }
	 * 
	 * } else if ((execution_id != null && !execution_id.equalsIgnoreCase("")) &&
	 * (status != null && !status.equalsIgnoreCase(""))) {
	 * System.out.println("Condition3");
	 * 
	 * executionDetailsqry =
	 * "SELECT `EXECUTION_MAPPER_ID`,`TESTCASE_NAME`,`TESTCASE_DESCRIPTION` FROM `EXECUTION_DETAILS_MAPPER` WHERE `PROJECT_ID`='"
	 * + projectId + "' and `TESTSUITE_ID`='" + testsuiteid + "' and EXECUTION_ID='"
	 * + execution_id + "' and `TESTCASE_RESULT`='" + status + "' ORDER BY `" +
	 * column + "` " + dir + " LIMIT " + start + "," + length; projectInfo =
	 * JDBCTemplate.select(executionDetailsqry, con);
	 * 
	 * }
	 * 
	 * con.commit(); } catch (SQLException e) { e.printStackTrace();
	 * log.error("SQLException caught while getIterationsBasedOnTestSuiteIdInfo");
	 * throw new
	 * TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
	 * "SQLException caught getIterationsBasedOnTestSuiteIdInfo"); } catch
	 * (Exception e) { e.printStackTrace();
	 * log.error("Exception caught while getIterationsBasedOnTestSuiteIdInfo");
	 * throw new
	 * TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
	 * "Exception caught while getIterationsBasedOnTestSuiteIdInfo"); } finally {
	 * try { if (con != null) { con.close(); con = null; } } catch (Exception e) {
	 * e.printStackTrace();
	 * log.error("Exception caught while closing the connection" + " message: " +
	 * e.getMessage()); throw new
	 * TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
	 * "Exception caught while closing the connection"); } } return countobj; }
	 * 
	 * 
	 * public JSONObject getIterationsBasedOnTestSuiteIdInfo1(String
	 * projectId,String testsuiteid,String status) { Connection con = null;
	 * JSONArray ExcutionInfo = new JSONArray(); JSONObject countobj = new
	 * JSONObject();
	 * 
	 * try { con = DatabaseConnection.getConnection(); JDBCTemplate JDBCTemplate =
	 * new JDBCTemplate(); // set auto commit to false String whereCondition=""; if
	 * (!projectId.equalsIgnoreCase("0")) { whereCondition = whereCondition +
	 * " AND `PROJECT_ID` = " + "'" + projectId + "'"; } if
	 * (!status.equalsIgnoreCase("0")) { whereCondition = whereCondition +
	 * " AND `TESTCASE_RESULT = " + "'" + status + "'"; } if
	 * (!testsuiteid.equalsIgnoreCase("0")) { whereCondition = whereCondition +
	 * " AND `TESTSUITE_ID` = " + "'" + testsuiteid + "'"; } String
	 * queryForExcutionDetails =
	 * "SELECT (SELECT COUNT(*) FROM `EXECUTION_DETAILS` WHERE 1 " + whereCondition
	 * +
	 * ") AS Count, `EXECUTION_TIME`,`ITERATION_NUMBER`,`EXECUTION_ID` FROM `EXECUTION_DETAILS` WHERE 1"
	 * + whereCondition; String queryForExcutionMapper =
	 * "SELECT `ITERATION_NUMBER`,`TESTCASE_RESULT`,`EXECUTION_ID`,`TESTCASE_NAME`,`TESTCASE_DESCRIPTION` FROM `EXECUTION_DETAILS_MAPPER` WHERE 1"
	 * + whereCondition; ExcutionInfo = JDBCTemplate.select(queryForExcutionDetails,
	 * con); for (int i = 0; i < ExcutionInfo.length(); i++) { JSONObject
	 * excutionObj = ExcutionInfo.getJSONObject(i); String executionId =
	 * excutionObj.getString("execution_id"); int pass = 0; int fail = 0; int skip =
	 * 0; int testcasecount = 0; queryForExcutionMapper =
	 * queryForExcutionMapper+" AND `EXECUTION_ID` = " + "'" + executionId + "'";
	 * 
	 * JSONArray excutionMapperInfo = JDBCTemplate.select(queryForExcutionMapper,
	 * con);
	 * 
	 * for (int j = 0; j < excutionMapperInfo.length(); j++) { JSONObject
	 * excutionMapperInfoObj = excutionMapperInfo.getJSONObject(j); if
	 * (excutionMapperInfoObj.getString("testcase_result").equalsIgnoreCase("Passed"
	 * )) { pass = pass+1; } else if
	 * (excutionMapperInfoObj.getString("testcase_result").equalsIgnoreCase("Failed"
	 * )) { fail = fail+1; } else if
	 * (excutionMapperInfoObj.getString("testcase_result").equalsIgnoreCase(
	 * "Skipped")) { skip = skip+1; } testcasecount = testcasecount+1; }
	 * ExcutionInfo.getJSONObject(i).put("skipped_testcase_count", skip);
	 * ExcutionInfo.getJSONObject(i).put("failed_testcase_count", fail);
	 * ExcutionInfo.getJSONObject(i).put("passed_testcase_count", pass);
	 * ExcutionInfo.getJSONObject(i).put("total_testcase_count",testcasecount);
	 * ExcutionInfo.getJSONObject(i).put("excution_mapper_info",
	 * excutionMapperInfo);
	 * 
	 * } countobj.put("count", ExcutionInfo.getJSONObject(0).getString("count"));
	 * countobj.put("excution_info", ExcutionInfo); } catch (JSONException e) {
	 * e.printStackTrace();
	 * log.error("JSONException caught while getIterationsBasedOnTestSuiteIdInfo");
	 * throw new
	 * TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
	 * "JSONException caught getIterationsBasedOnTestSuiteIdInfo"); } catch
	 * (Exception e) { e.printStackTrace();
	 * log.error("Exception caught while getIterationsBasedOnTestSuiteIdInfo");
	 * throw new
	 * TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
	 * "Exception caught while getIterationsBasedOnTestSuiteIdInfo"); } finally {
	 * try { if (con != null) { con.close(); con = null; } } catch (Exception e) {
	 * e.printStackTrace();
	 * log.error("Exception caught while closing the connection" + " message: " +
	 * e.getMessage()); throw new
	 * TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
	 * "Exception caught while closing the connection"); } } return countobj; }
	 */
	public JSONArray getRemoteMachineDetails(String project_id) {

		JSONArray remotemachinedetails = new JSONArray();
		Connection connection = DatabaseConnection.getConnection();
		JSONObject mainObject = new JSONObject();

		try {

			remotemachinedetails = new JDBCTemplate().selectByColumnName("TARGET_REMOTE_MACHINES", "PROJECT_ID",
					project_id, connection);
		} finally {
			try {
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}

		return remotemachinedetails;

	}

	public JSONArray getTestCasePassOrFailStepsExecutionDetails_ExecutionIDInfo(String testcase_name, String start,
			String length, String column, String dir, String execution_id) {
		Connection con = null;
		JSONArray projectInfo = new JSONArray();
		JSONArray mainArray = new JSONArray();

		try {
			con = DatabaseConnection.getConnection();
			JDBCTemplate JDBCTemplate = new JDBCTemplate();
			// set auto commit to false
			String userDetailsQuery = "";
			userDetailsQuery = "SELECT `TESECASE_STEPS_RESULTS_MAPPER_ID`,`TESTSTEP_NUMBER`, `USER_ACTION`, `ACTUAL_RESULT`, `STEPRESULT`, `EXECUTION_TIME`,`IMAGE_PATH`, `IMAGE_NAME`, `IMAGE_DBNAME`, `IMAGE_FORMAT`, `TESTCASE_NAME`, `TESTSUITE_NAME` FROM `TESECASE_STEPS_RESULTS_MAPPER`WHERE EXECUTION_ID='"
					+ execution_id + "' and TESTCASE_NAME='" + testcase_name + "' ORDER BY `" + column + "` " + dir
					+ " LIMIT " + start + "," + length;// stepresult
			projectInfo = JDBCTemplate.select(userDetailsQuery, con);
			for (int i = 0; i < projectInfo.length(); i++) {
				JSONObject object = projectInfo.getJSONObject(i);
				if(!object.getString("image_path").equalsIgnoreCase(""))
				{
				File file = new File(object.getString("image_path"));
				byte[] fileContent = FileUtils.readFileToByteArray(file);
				String encodedString = Base64.getEncoder().encodeToString(fileContent);
				object.put("Image_base", encodedString);
				String ACTUAL_RESULT = object.getString("actual_result");
				System.out.println("actual_result:" + ACTUAL_RESULT);
				byte[] decodestring = Base64.getDecoder().decode((ACTUAL_RESULT.getBytes()));
				object.put("actual_result", new String(decodestring));

				}else {
					object.put("actual_result",object.getString("actual_result"));
					object.put("Image_base","");
				}
				// writeImagetoTemporaryFolder(object.getString("image_path"));

				mainArray.put(object);
			}

		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getTestCasePassOrFailStepsExecutionDetails_ExecutionIDInfo");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getTestCasePassOrFailStepsExecutionDetails_ExecutionIDInfo");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return mainArray;
	}
	public JSONArray getTestCasePassOrFailStepsExecutionDetailsExecutionIDWithoutPaginationInfo(JSONObject inputobject) {
		Connection con = null;
		JSONArray projectInfo = new JSONArray();
		JSONArray mainArray = new JSONArray();

		try {
			con = DatabaseConnection.getConnection();
			JDBCTemplate JDBCTemplate = new JDBCTemplate();
			// set auto commit to false
			String userDetailsQuery = "";
			userDetailsQuery = "SELECT `TESECASE_STEPS_RESULTS_MAPPER_ID`,`TESTSTEP_NUMBER`, `USER_ACTION`, `ACTUAL_RESULT`, `STEPRESULT`, `EXECUTION_TIME`,`IMAGE_PATH`, `IMAGE_NAME`, `IMAGE_DBNAME`, `IMAGE_FORMAT`, `TESTCASE_NAME`, `TESTSUITE_NAME` FROM `TESECASE_STEPS_RESULTS_MAPPER`WHERE EXECUTION_ID='"
					+ inputobject.getString("execution_id")+ "' and TESTCASE_NAME='" + inputobject.getString("testcase_name")+"'"; 
					// stepresult
			projectInfo = JDBCTemplate.select(userDetailsQuery, con);
			for (int i = 0; i < projectInfo.length(); i++) {
				JSONObject object = projectInfo.getJSONObject(i);
				if(!object.getString("image_path").equalsIgnoreCase(""))
				{
				File file = new File(object.getString("image_path"));
				byte[] fileContent = FileUtils.readFileToByteArray(file);
				String encodedString = Base64.getEncoder().encodeToString(fileContent);
				object.put("Image_base", encodedString);
				String ACTUAL_RESULT = object.getString("actual_result");
				System.out.println("actual_result:" + ACTUAL_RESULT);
				byte[] decodestring = Base64.getDecoder().decode((ACTUAL_RESULT.getBytes()));
				}else {
					object.put("actual_result",object.getString("actual_result"));
					object.put("Image_base","");
				}
				
				// writeImagetoTemporaryFolder(object.getString("image_path"));

				mainArray.put(object);
			}

		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getTestCasePassOrFailStepsExecutionDetails_ExecutionIDInfo");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while getTestCasePassOrFailStepsExecutionDetails_ExecutionIDInfo");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
		return mainArray;
	}

	public int writeImagetoTemporaryFolder(String filepath) {
		int status = 0;

		try {
			File file = new File(filepath);
			BufferedImage image = null;
			String name[] = filepath.split("/");
			String imagename = name[name.length - 1];
			if (imagename.contains(".")) {

			} else {
				imagename = imagename + ".png";
			}

			PropertiesConfiguration config = new PropertiesConfiguration("db.properties");
			String directory_name = (String) config.getProperty("db.webapp") + File.separator + name[name.length - 1];

			// String
			// directory_name="/home/pavan/Music/tomcat7/webapps/TSETemporaryImages/"+imagename;
			image = ImageIO.read(file);
			ImageIO.write(image, "png", new File(directory_name));
			status = 1;
		} catch (Exception e) {
			log.error("Exception caught writing image ");
			e.printStackTrace();
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught writing image ");
		}
		return status;
	}

	private static boolean createDirectory(String path) {
		System.out.println("path:::" + path);
		File file = new File(path);
		/*
		 * if (!file.exists()) { file.mkdir(); return true; } return false;
		 */
		if (file.exists()) {
			return true;
		} else {
			file.mkdirs();
			return true;
		}

	}

	public static boolean createPdfFolders(JSONArray foldersNames, String projectName) {

		Configuration config;
		try {
			config = new PropertiesConfiguration("db.properties");
			String path = (String) config.getProperty("db.tse.image.filepath") + File.separator + projectName;
			if (createDirectory(path)) {
				for (int i = 0; i < foldersNames.length(); i++) {
					createDirectory(path + File.separator + foldersNames.getString(i));
				}
				if (log.isDebugEnabled()) {
					log.debug("Pdf Folders Created Successfully");
				}
			} else {
				if (log.isDebugEnabled()) {
					log.debug("Pdf Folders Not Created");
				}
				return false;
			}
			return true;
		} catch (Exception e) {
			log.error("Exception caught while createPdfFolders ");
			e.printStackTrace();
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while createPdfFolders ");
		}

	}

	public String generateBase64EncodedRandomString(int length) {

		String string = RandomStringUtils.random(length, true, true) + System.nanoTime();

		return (Base64.getEncoder().encodeToString(string.getBytes())).substring(0, length - 1);
	}

	public int insertexecuteTestcaseStepsInfo(JSONObject object) {

		Connection con = DatabaseConnection.getConnection();
		try {
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			Configuration config;
			JSONArray foldersaArray = new JSONArray();
			foldersaArray.put("images");
			createPdfFolders(foldersaArray, object.getString("project_name"));
			config = new PropertiesConfiguration("db.properties");
			String directory_name = (String) config.getProperty("db.tse.image.filepath") + File.separator
					+ object.getString("project_name") + File.separator + "images/";

			byte[] result = Base64.getDecoder().decode(object.getString("image_data"));
			ByteArrayInputStream bis = new ByteArrayInputStream(result);
			BufferedImage bImage2 = ImageIO.read(bis);
			String filename = "";
			if (object.getString("image_format").contains(".")) {
				filename = object.getString("execution_id") + "_" + object.getString("testcase_name") + "_"
						+ object.getString("teststep_number") + object.getString("image_format");

			} else {
				filename = object.getString("execution_id") + "_" + object.getString("testcase_name") + "_"
						+ object.getString("teststep_number") + "." + object.getString("image_format");
			}
			String db_filename = generateBase64EncodedRandomString(5);

			File file = new File(directory_name + filename);
			ImageIO.write(bImage2, "jpg", file);
			String image_path = file.getAbsolutePath();
			object.put("image_path", image_path);
			object.put("image_name", filename);
			object.put("image_dbname", db_filename);
			object.remove("image_data");
			System.out.println("objectobjectobjectobject:" + object);

			jdbcTemplate.insert("TESECASE_STEPS_RESULTS_MAPPER", object, con);
			return 1;
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("Exception caught while insertexecuteTestcaseStepsInfo");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while insertexecuteTestcaseStepsInfo");
		} catch (IOException e) {
			e.printStackTrace();

			log.error("Exception caught while insertexecuteTestcaseStepsInfo");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while insertexecuteTestcaseStepsInfo");
		} catch (ConfigurationException e) {
			e.printStackTrace();

			log.error("Exception caught while insertexecuteTestcaseStepsInfo");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while insertexecuteTestcaseStepsInfo");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
	}

	public int insertTestCaseExecutionStatusForNotificationsInfo(JSONObject object) {

		Connection con = DatabaseConnection.getConnection();
		try {
			con.setAutoCommit(false);
			new JDBCTemplate().insert("TESECASE_EXECUTION_STATUS", object, con);
			con.commit();
			return 1;
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while insertTestCaseExecutionStatusForNotificationsInfo");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught while insertTestCaseExecutionStatusForNotificationsInfo");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}
	}

	public JSONArray readAfterDeleteTestCaseExecutionStatus(String execution_id) {
		JSONArray jsonArray = new JSONArray();
		Connection connection = DatabaseConnection.getConnection();
		JSONObject mainObject = new JSONObject();

		try {
			JDBCTemplate template = new JDBCTemplate();
			String query = "SELECT TESTCASE_NAME,STATUS FROM `TESECASE_EXECUTION_STATUS` WHERE `EXECUTION_ID`='"
					+ execution_id + "' ORDER BY `TESECASE_EXECUTION_STATUS_CREATED_TIME` DESC limit 1";
			System.out.println("query:" + query);
			jsonArray = template.select(query, connection);
			/*
			 * String deleteqry =
			 * "DELETE FROM `TESECASE_EXECUTION_STATUS` WHERE `EXECUTION_ID`='" +
			 * execution_id + "'"; template.delete(deleteqry, connection);
			 */
		} finally {
			try {
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}

		return jsonArray;
	}

	public String runRemoteScriptFile(JSONObject object) {
		String status = "Invalid";
		Process p = null;
		try {

			if (object.getString("target_remote_machine_os").equalsIgnoreCase("windows")) {
				System.out.println(object.getString("target_remote_machine_project_path") + "batch.bat");
				ProcessBuilder builder = new ProcessBuilder(
						object.getString("target_remote_machine_project_path") + "batch.bat");
				p = builder.start();
				BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
				String line = null;
				StringBuffer sb = new StringBuffer("");

				while (reader.readLine() != null) {

					System.out.println(reader.readLine());
					sb.append(reader.readLine());
				}

				status = "Success";

				p.destroy();

			} else if (object.getString("target_remote_machine_os").equalsIgnoreCase("linux")) {

			}
			return status;

		} catch (IOException e) {
			e.printStackTrace();
			log.error("Exception caught while Executing runRemoteScriptFile");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while Executing runRemoteScriptFile");
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("Exception caught while Executing runRemoteScriptFile");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while Executing runRemoteScriptFile");
		} finally {
			if (p != null) {
				p.destroy();
			}

		}
	}

	public String deleteProject(String project_id, String user_id, String project_name) {
		String response = "99";
		try {
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			Connection con = DatabaseConnection.getConnection();
			con.setAutoCommit(false);
			if (!project_id.equalsIgnoreCase("")) {

				String query = "UPDATE `PROJECTS_INFO` SET `PROJECT_STATUS`='0' WHERE `PROJECT_ID`='" + project_id
						+ "'";
				jdbcTemplate.updateByQuery(query, con);
				response = "00";

			}
			insertRecentActivites("Project " + project_name + " was deleted", user_id, con);
			con.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while  delete Project");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught while delete Scheduler");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while   delete Project");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while  delete Project");
		}

		return response;
	}

	public JSONArray getDefetcttoolsBasedOnProjectIdInfo(String project_id) {
		JSONArray remotemachinedetails = new JSONArray();
		Connection connection = DatabaseConnection.getConnection();

		try {

			remotemachinedetails = new JDBCTemplate().selectByColumnName("PROJECTS_INFO", "PROJECT_ID", project_id,
					connection);
		} finally {
			try {
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}

		return remotemachinedetails;

	}

	//

	public String insertSchedulerDetails(JSONObject object) {
		System.out.println("object::::" + object);
		String response = "0";
		Connection con = null;
		String insertShadularId = "";
		String schedulertype = "";
		String type = "";
		String cronExpression = "";
		String triggergroup="";
		String days="";
		String testsuite_id="";
		String project_id="";
		String[] dates = new String[31];

		try {
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			con = DatabaseConnection.getConnection();
			con.setAutoCommit(false);
			schedulertype = object.getString("scheduler_type");
			type = object.getString("type");
			testsuite_id = object.getString("testsuite_id");
			project_id = object.getString("project_id");

			String scheduler_id = object.getString("scheduler_id");
			JSONObject inserShadularObj = new JSONObject();

			/*
			 * inserShadularObj.put("user_id", object.getString("user_id"));
			 * inserShadularObj.put("project_id", object.getString("project_id"));
			 * inserShadularObj.put("testsuite_id", object.getString("testsuite_id"));
			 * inserShadularObj.put("scheduler_name", object.getString("scheduler_name"));
			 * inserShadularObj.put("username", object.getString("username"));
			 * inserShadularObj.put("scheduler_type",schedulertype );
			 * inserShadularObj.put("startdate", object.getString("startdate"));
			 * inserShadularObj.put("enddate", object.getString("enddate"));
			 * inserShadularObj.put("target_remote_machine_id",
			 * object.getString("target_remote_machine_id"));
			 * inserShadularObj.put("remote_machine_ip",
			 * object.getString("remote_machine_ip"));
			 * inserShadularObj.put("remote_machine_username",
			 * object.getString("remote_machine_username"));
			 * inserShadularObj.put("remote_machine_password",
			 * object.getString("remote_machine_password"));
			 * inserShadularObj.put("target_remote_machine_project_path",object.getString(
			 * "target_remote_machine_project_path"));
			 * inserShadularObj.put("target_remote_machine_os",
			 * object.getString("target_remote_machine_os"));
			 * inserShadularObj.put("target_remote_machine_script_version",object.getString(
			 * "target_remote_machine_script_version"));
			 * inserShadularObj.put("project_name", object.getString("project_name"));
			 * inserShadularObj.put("testsuite_name", object.getString("testsuite_name"));
			 * inserShadularObj.put("job_triggering_time","");
			 * inserShadularObj.put("script_duration_time","");
			 */

			JSONArray testcaseDataArray = object.getJSONArray("testcase_data");
			object.remove("testcase_data");
			if (type.equalsIgnoreCase("EveryHour")) {
				cronExpression = "0 0 * ? * * *";
				// cronExpression = "0 */5 * ? * *";
				triggergroup = "EveryHour";

				System.out.println("CronExpression For Evary Hour:" + cronExpression);
			} else if (type.equalsIgnoreCase("EveryHourParticulartime")) {
				cronExpression = "0" + " " + "0" + " " + object.getString("startingat") + "/"
						+ object.getString("hours") + " " + "? * * *";
				System.out.println("CronExpression For EveryHourParticulartime:" + cronExpression);
				triggergroup = "EveryHourParticulartime";


			} else if (type.equalsIgnoreCase("EveryBetweenhours")) {
				cronExpression = "0 0" + " " + object.getString("hoursstart") + "-" + object.getString("hoursend")
						+ " ? * * *";
				System.out.println("CronExpression For EveryBetweenhours:" + cronExpression);
				triggergroup = "EveryBetweenhours";


			}
			if (type.equalsIgnoreCase("EveryDay")) {
				// s1="0"+" "+EveryDayMinutesStartat+" "+EveryDayhourstartat+" "+"?"+" "+hsmon+"
				// "+"*"+" "+hsyear;
				cronExpression = "0" + " " + object.getString("EveryDayMinutesStartat") + " "
						+ object.getString("EveryDayhourstartat") + " ? * * *";
				log.info("Cron Expression For Every Day:" + cronExpression);
				triggergroup = "EveryDay";
				System.out.println("Cron Expression For Every Day:" + cronExpression);


			} else if (type.equalsIgnoreCase("EveryParticularday")) {
				// s1="0"+" "+EveryDayMinutesStartat+" "+EveryDayhourstartat+"
				// "+1+"/"+particlarstartday+" "+hsmon+" "+"?"+" "+hsyear;
				cronExpression = "0" + " " + object.getString("EveryDayMinutesStartat") + " "
						+ object.getString("EveryDayhourstartat") + " " + object.getString("particlarstartday") + "/"
						+ object.getString("everyrepeatedday") + " * ? *";
				// 0 24 3 1/17 * ? *
				log.info("Cron Expression For Every Day Starts at Specific Day:" + cronExpression);
				System.out.println("Cron Expression For Every Day Starts at Specific Day:" + cronExpression);
				triggergroup = "EveryParticularday";

			} else if (type.equalsIgnoreCase("everyScelectedDay")) {
				// s1="0"+" "+EveryDayhourstartat+" "+EveryDayhourstartat+" "+days+" "+hsmon+"
				// "+"?"+" "+hsyear;
				// 0 30 3 14,17 * ? *
				String EveryDayhourstartat = object.getString("SpecificDayhour");
				String EveryDayMinutesStartat = object.getString("SpecificMinutes");

				if (object.getBoolean("one")) {
					dates[0] = "1";
					days = "1,";
				} else if (object.getBoolean("two")) {
					dates[1] = "2";
					days = days + "2,";

				} else if (object.getBoolean("three")) {
					dates[2] = "3";
					days = days + "3,";

				} else if (object.getBoolean("four")) {
					dates[3] = "4";
					days = days + "4,";

				} else if (object.getBoolean("five")) {
					dates[4] = "5";
					days = days + "5,";

				} else if (object.getBoolean("six")) {
					dates[5] = "6";
					days = days + "6,";

				} else if (object.getBoolean("seven")) {
					dates[6] = "7";
					days = days + "7,";

				} else if (object.getBoolean("eight")) {
					dates[7] = "8";
					days = days + "8,";

				} else if (object.getBoolean("nine")) {
					dates[8] = "9";
					days = days + "9,";

				} else if (object.getBoolean("ten")) {
					dates[9] = "10";
					days = days + "10,";

				} else if (object.getBoolean("eleven")) {
					dates[10] = "11";
					days = days + "11,";

				} else if (object.getBoolean("twelve")) {
					dates[11] = "12";
					days = days + "12,";

				} else if (object.getBoolean("thirteen")) {
					dates[12] = "13";
					days = days + "13,";

				} else if (object.getBoolean("fourteen")) {
					dates[13] = "14";
					days = days + "14,";

				} else if (object.getBoolean("fifteen")) {
					dates[14] = "15";
					days = days + "15,";
				} else if (object.getBoolean("sixteen")) {
					dates[15] = "16";
					days = days + "16,";

				} else if (object.getBoolean("seventeen")) {
					dates[16] = "17";
					days = days + "17,";

				} else if (object.getBoolean("eighteen")) {
					dates[17] = "18";
					days = days + "18,";

				} else if (object.getBoolean("ninteen")) {
					dates[18] = "19";
					days = days + "19,";

				} else if (object.getBoolean("twenty")) {
					dates[19] = "20";
					days = days + "20,";

				} else if (object.getBoolean("twentyone")) {
					dates[20] = "21";
					days = days + "21,";

				} else if (object.getBoolean("twentytwo")) {
					dates[21] = "22";
					days = days + "22,";

				} else if (object.getBoolean("twentythree")) {
					dates[22] = "23";
					days = days + "23,";

				} else if (object.getBoolean("twentyfour")) {
					dates[23] = "24";
					days = days + "24,";

				} else if (object.getBoolean("twentyfive")) {
					dates[24] = "25";
					days = days + "25,";

				} else if (object.getBoolean("twentysix")) {
					dates[25] = "26";
					days = days + "26,";

				} else if (object.getBoolean("twentyseven")) {
					dates[26] = "27";
					days = days + "27,";

				} else if (object.getBoolean("twentyeight")) {
					dates[27] = "28";
					days = days + "28,";

				} else if (object.getBoolean("twentynine")) {
					dates[28] = "29";
					days = days + "29,";

				} else if (object.getBoolean("thirty")) {
					dates[29] = "30";
					days = days + "30,";

				} else if (object.getBoolean("thirtyone")) {
					dates[30] = "31";
					days = days + "31";

				}

				cronExpression = "0" + " " + EveryDayMinutesStartat + " " + EveryDayhourstartat + " " + days + " * ? *";
				triggergroup = "everyScelectedDay";

				log.info("Cron Expression For Every Day Starts at Specific Day:" + cronExpression);
				System.out.println("Cron Expression For Every Day Starts at Specific Day:" + cronExpression);

			} else if (type.equalsIgnoreCase("Weekly")) {

				cronExpression = "0" + " " + object.getString("minutes") + " " + object.getString("hours") + " ? * "
						+ object.getString("weekday") + " *";
				triggergroup = "Weekly";
				System.out.println("Cron Expression For Weekly:" + cronExpression);


			}
			else if (schedulertype.equalsIgnoreCase("Monthly")) {
				cronExpression = "0" + " " + object.getString("minutes") + " " + object.getString("hours") + " "
						+ object.getString("type") + " " + object.getString("month") + " " + "? *";
				triggergroup = "Monthly";
				System.out.println("Cron Expression For Monthly:" + cronExpression);
			}

			if (scheduler_id.equalsIgnoreCase("")) {
				object.remove("scheduler_id");
				object.remove("testcase_data");
				insertShadularId = String.valueOf(jdbcTemplate.insert("SCHEDULER", object, con));
				testcaseDataArray = addKey(testcaseDataArray, "scheduler_id", insertShadularId);
				testcaseDataArray = addKey(testcaseDataArray, "testsuite_id", testsuite_id);
				testcaseDataArray = addKey(testcaseDataArray, "project_id", project_id);

				if (testcaseDataArray.length() != 0) {
					for (int i = 0; i < testcaseDataArray.length(); i++) {
						JSONObject temptestcaseObj = testcaseDataArray.getJSONObject(i);
						JSONObject tempObj = new JSONObject();
						temptestcaseObj.remove("$$hashKey");
						temptestcaseObj.remove("Selected");
						tempObj.put("project_id", project_id);
						tempObj.put("user_id", temptestcaseObj.getString("user_id"));
						tempObj.put("testsuite_id", testsuite_id);
						System.out.println("234545555555555555555555555:"
								+ temptestcaseObj.getJSONArray("test_data").getJSONObject(0).toString());
						tempObj.put("testcase_data", temptestcaseObj.toString());
						tempObj.put("scheduler_id", insertShadularId);
						tempObj.put("project_testcase_id", temptestcaseObj.getString("project_testcase_id"));
						tempObj.put("testcase_name", temptestcaseObj.getString("testcasename"));
						tempObj.put("description", temptestcaseObj.getString("description"));
						tempObj.put("testtype", temptestcaseObj.getString("testtype"));
						tempObj.put("module", temptestcaseObj.getString("module"));
						tempObj.put("priority", temptestcaseObj.getString("priority"));
						tempObj.put("designer", temptestcaseObj.getString("designer"));
						tempObj.put("release_version", temptestcaseObj.getString("release_version"));
						tempObj.put("automation_feasibility", temptestcaseObj.getString("automation_feasibility"));
						tempObj.put("execution_type", temptestcaseObj.getString("execution_type"));
						tempObj.put("requirement", temptestcaseObj.getString("requirement"));

						System.out.println("tempObj:" + tempObj);
						testcaseDataArray.put(i, tempObj);
					}
				}
				System.out.println("testcaseDataArray1:" + testcaseDataArray);
				jdbcTemplate.insert("TESECASE_MAPPER", testcaseDataArray, con);
				response = "1";

			} else {
				JSONArray updateArray = new JSONArray();
				JSONObject inputUpdate = new JSONObject();
				JSONObject filterObj = new JSONObject();
				filterObj.put("scheduler_id", scheduler_id);
				inputUpdate.put("update_data", inserShadularObj);
				inputUpdate.put("filter_data", filterObj);
				updateArray.put(inputUpdate);
				jdbcTemplate.update("SCHEDULER", updateArray, con);
				insertShadularId = scheduler_id;
				response = "1";

			}
			System.out.println("insertShadularId:" + insertShadularId);
			/*
			 * if (!insertShadularId.equalsIgnoreCase("")) {
			 * System.out.println("11111111111"); response = Integer.toString( new
			 * SchedularCron().cronMaker(insertShadularId, object.getString("startdate") +
			 * " 00:00:00", object.getString("enddate") + " 23:59:59",
			 * cronExpression,triggergroup));
			 * 
			 * } else { response = "3"; }
			 */
			System.out.println("response:" + response);
			con.commit();
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while insertShadularaDetails");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while insertShadularaDetails");
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while insertShadularaDetails");
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"SQLException caught while insertShadularaDetails");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while insertShadularaDetails");
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"Exception caught while insertShadularaDetails");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}

		return response;
	}

	public JSONObject excuiteShadularDetails(int shadular_id) {
		JSONObject result = new JSONObject();
		String response = "0";
		String excuition_id = "0";
		Connection con = null;
		try {
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			con = DatabaseConnection1.getConnection();
			String selectShadularQuery = "SELECT * FROM `SCHEDULER` WHERE `SCHEDULER_ID`='" + shadular_id + "'";
			JSONArray shadularaDataArray = jdbcTemplate.select(selectShadularQuery, con);
			if (shadularaDataArray.length() != 0) {
				int iteration_number = 0;
				for (int j = 0; j < shadularaDataArray.length(); j++) {

					JSONArray excuitionMapperData = new JSONArray();

					JSONObject shadularData = shadularaDataArray.getJSONObject(j);
					JSONObject excuitionObj = new JSONObject();
					String testsuite_id = shadularData.getString("testsuite_id");
					String project_id = shadularData.getString("project_id");
					String scheduler_id = shadularData.getString("scheduler_id");
					String user_id = shadularData.getString("user_id");
					String target_remote_machine_id = shadularData.getString("target_remote_machine_id");
					if (iteration_number == 0) {
						String query = "SELECT MAX(ITERATION_NUMBER) as ITERATION_NUMBER FROM `EXECUTION_DETAILS` where PROJECT_ID='"
								+ project_id + "' and TESTSUITE_ID='" + testsuite_id + "'";
						System.out.println("Query2::" + query);
						JSONArray array = jdbcTemplate.select(query, con);
						System.out.println("Arrray--------------------------" + array);
						if (array.length() != 0) {
							JSONObject itrobj = array.getJSONObject(0);
							if (itrobj.has("iteration_number")) {
								iteration_number = Integer.parseInt(itrobj.getString("iteration_number").trim());
							}
						}
					}
					iteration_number = iteration_number + 1;
					excuitionObj.put("iteration_number", iteration_number+"");
					excuitionObj.put("user_id", shadularData.getString("user_id"));
					excuitionObj.put("project_id", shadularData.getString("project_id"));
					excuitionObj.put("testsuite_id", shadularData.getString("testsuite_id"));
					excuitionObj.put("project_name", shadularData.getString("project_name"));
					excuitionObj.put("testsuite_name", shadularData.getString("testsuite_name"));
					excuitionObj.put("target_remote_machine_id", shadularData.getString("target_remote_machine_id"));
					excuitionObj.put("remote_machine_ip", shadularData.getString("remote_machine_ip"));
					excuitionObj.put("remote_machine_username", shadularData.getString("remote_machine_username"));
					excuitionObj.put("remote_machine_password", shadularData.getString("remote_machine_password"));
					excuitionObj.put("target_remote_machine_project_path",
							shadularData.getString("target_remote_machine_project_path"));
					excuitionObj.put("target_remote_machine_os", shadularData.getString("target_remote_machine_os"));
					excuitionObj.put("target_remote_machine_script_version",
							shadularData.getString("target_remote_machine_script_version"));
					excuitionObj.put("scheduler_id", scheduler_id);
					excuition_id = String.valueOf(jdbcTemplate.insert("EXECUTION_DETAILS", excuitionObj, con));

					String querySelectshadularTestCase = "SELECT * FROM `TESECASE_MAPPER` WHERE `PROJECT_ID`='"
							+ project_id + "' AND `TESTSUITE_ID`='" + testsuite_id + "' AND `SCHEDULER_ID` ='"
							+ scheduler_id + "'";
					JSONArray shadularTestCaseArray = jdbcTemplate.select(querySelectshadularTestCase, con);
					for (int i = 0; i < shadularTestCaseArray.length(); i++) {
						JSONObject shadularTestCaseObj = shadularTestCaseArray.getJSONObject(i);
						JSONObject execution_mapper_obj = new JSONObject();
						execution_mapper_obj.put("execution_id", excuition_id);
						execution_mapper_obj.put("project_id", project_id);
						execution_mapper_obj.put("user_id", user_id);
						execution_mapper_obj.put("testsuite_id", testsuite_id);
						execution_mapper_obj.put("scheduler_id", scheduler_id);
						execution_mapper_obj.put("testcase_name", shadularTestCaseObj.getString("testcase_name"));
						execution_mapper_obj.put("testcase_data", shadularTestCaseObj.getString("testcase_data"));
						excuitionMapperData.put(execution_mapper_obj);
					}
					jdbcTemplate.insert("EXECUTION_DETAILS_MAPPER", excuitionMapperData, con);
					String updatetargetMichine = "UPDATE `TARGET_REMOTE_MACHINES` SET `EXECUTION_ID`= '" + excuition_id
							+ "', `TARGET_REMOTE_MACHINE_STATUS` = '1' WHERE `TARGET_REMOTE_MACHINE_ID`='"
							+ target_remote_machine_id + "'";
					jdbcTemplate.updateByQuery(updatetargetMichine, con);
					response = "1";
				}

			}
			result.put("response", response);
			result.put("execution_id", excuition_id);
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while excuiteShadularDetails");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while excuiteShadularDetails");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while excuiteShadularDetails");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while excuiteShadularDetails");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}

		return result;
	}

	public String validateRemoteMichine(String target_remote_machine_id) {

		String result = "99";
		Connection connection = DatabaseConnection.getConnection();

		try {
			JDBCTemplate template = new JDBCTemplate();
			String query = "SELECT TARGET_REMOTE_MACHINE_ID FROM `TARGET_REMOTE_MACHINES` WHERE `TARGET_REMOTE_MACHINE_ID` ='"
					+ target_remote_machine_id + "' AND `EXECUTION_ID` ='' AND `TARGET_REMOTE_MACHINE_STATUS` ='0'";
			System.out.println("query:" + query);
			JSONArray jsonArray = template.select(query, connection);
			if (jsonArray.length() != 0) {
				String validateShadule = "SELECT * FROM SCHEDULER WHERE (NOW( ) BETWEEN `JOB_TRIGGERING_TIME` AND `SCRIPT_DURATION_TIME`) AND `TARGET_REMOTE_MACHINE_ID`='"
						+ target_remote_machine_id + "' AND SCHEDULER_ACTIVE_STATUS ='1'";
				JSONArray shaduleArray = template.select(validateShadule, connection);
				if (shaduleArray.length() == 0) {
					result = "00";
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.error("SQLException caught while validateRemoteMichine");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught while validateRemoteMichine");
		} finally {
			try {
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}

		return result;

	}

	public int updateTestSuitesLevelResultsController(JSONObject object) {
		JSONObject whereCondition = new JSONObject();
		JSONObject upDateObj = new JSONObject();
		JSONObject executionDetailsupdateObj = new JSONObject();
		JSONArray updateDataArray = new JSONArray();
		Connection con = DatabaseConnection.getConnection();
		String status = "InValidData";
		int failed = 0;
		try {
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			con.setAutoCommit(false);

			executionDetailsupdateObj.put("skipped_testcase_count", object.getString("skipped_testcase_count"));
			executionDetailsupdateObj.put("passed_testcase_count", object.getString("passed_testcase_count"));
			executionDetailsupdateObj.put("failed_testcase_count", object.getString("failed_testcase_count"));
			executionDetailsupdateObj.put("total_testcase_count", object.getString("total_testcase_count"));

			if (Integer.parseInt(object.getString("failed_testcase_count")) > 0) {
				executionDetailsupdateObj.put("execution_overallstatus", "Failed");
				failed = 1;
			} else {
				executionDetailsupdateObj.put("execution_overallstatus", "Passed");

			}
			whereCondition.put("execution_id", object.getString("execution_id"));
			upDateObj.put("filter_data", whereCondition);
			upDateObj.put("update_data", executionDetailsupdateObj);
			updateDataArray.put(upDateObj);
			System.out.println("updateDataArray:" + updateDataArray);
			jdbcTemplate.update("EXECUTION_DETAILS", updateDataArray, con);

			/* Update Execution Details Mapper */
			String updateqry = "UPDATE `TARGET_REMOTE_MACHINES` SET `TARGET_REMOTE_MACHINE_STATUS`='0' where `EXECUTION_ID`=' '";
			jdbcTemplate.updateByQuery(updateqry, con);
			con.commit();
			System.out.println("objectobjectobject" + object);
			//sendMailtoUsers(object);
			if (failed > 0) {
				raiseTheBugsInDefecttools(object.getString("execution_id"));
			}

			return 0;
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while update Execution Details");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while update Execution Details");
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while update Execution Details");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught while update Execution Details");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while update Execution Details");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"Exception caught while update Execution Details");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}

	}

	public int raiseTheBugsInDefecttools(String executionId) {
		Connection con = DatabaseConnection.getConnection();
		JDBCTemplate template = new JDBCTemplate();

		try {
			String selectqry = "SELECT * FROM `TESECASE_STEPS_RESULTS_MAPPER` WHERE `STEPRESULT` = 'Failed' and `EXECUTION_ID`='"
					+ executionId + "'";
			JSONArray testcasearray = template.select(selectqry, con);
			for (int i = 0; i < testcasearray.length(); i++) {
				JSONObject object = testcasearray.getJSONObject(i);
				HashMap<Integer, String> oldbugmap = new HashMap<>();
				HashMap<Integer, String> newbugmap = new HashMap<>();
				String project_id = object.getString("project_id");
				String project_name = object.getString("project_name");
				String testcase_name = object.getString("testcase_name");
				int teststep_number = object.getInt("teststep_number");
				String user_action = object.getString("user_action");
				String actual_result = object.getString("actual_result");
				newbugmap.put(teststep_number, actual_result);
				String getjirabuginfo = "SELECT * FROM TSE_JIRA_BUG_DETAILS where TESTSTEP_NUMBER='" + teststep_number
						+ "' and USER_ACTION='" + user_action + "' and project_id='" + project_id
						+ "' and ACTUAL_RESULT='" + actual_result + "' and BUG_STATUS='Open'";
				JSONArray buginfoarray = template.select(getjirabuginfo, con);
				int defectexist = 0;
				if (buginfoarray.length() != 0)

				{
					for (int k = 0; k < buginfoarray.length(); k++) {
						JSONObject oldinfoobject = buginfoarray.getJSONObject(k);
						String oldinfoqry = "select * from EXECUTION_DETAILS_MAPPER where EXECUTION_ID='"
								+ oldinfoobject.getString("execution_id") + "' and TESTCASE_NAME='"
								+ oldinfoobject.getString("testcase_name") + "' and TESTCASE_RESULT='Failed'";
						if (oldbugmap.equals(newbugmap)) {
							defectexist = 1;
							String updateqry = "UPDATE `EXECUTION_DETAILS_MAPPER` SET `DEFECT_ID`='"
									+ oldinfoobject.getString("bug_id") + "' WHERE `project_id`='" + project_id
									+ "' and `TESTCASE_NAME`='" + testcase_name
									+ "' and `TESTCASE_RESULT`='Failed' and `DEFECT_ID`='' OR DefectId IS NULL";
							log.info("updateqry:" + updateqry);
							template.updateByQuery(updateqry, con);
						} else {
							defectexist = 0;
							break;
						}

						if (defectexist == 0) {
							JSONObject object2 = new JSONObject();
							object2.put("project_id", project_id);
							object2.put("ExecutionId", executionId);
							object2.put("product", project_name);
							object2.put("component", testcase_name);
							log.info(object2.toString());
							new Jira().createIsssue(object2);
						} else {
							JSONObject object2 = new JSONObject();
							object2.put("project_id", project_id);
							object2.put("ExecutionId", executionId);
							object2.put("product", project_name);
							object2.put("component", testcase_name);
							object2.put("bugId", oldinfoobject.getString("bug_id"));
							log.info(object2.toString());
							new Jira().updateIsssue(object2);
						}

					}

				} else {
					JSONObject object2 = new JSONObject();
					object2.put("project_id", project_id);
					object2.put("ExecutionId", executionId);
					object2.put("product", project_name);
					object2.put("component", testcase_name);
					log.info(object2.toString());
					new Jira().createIsssue(object2);
				}
				return 1;

			}
		}

		catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while posting a bug in jira" + " message: " + e.getMessage());
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"Exception caught while posting a bug in jira");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}
		return 1;

	}

	public void sendMailtoUsers(JSONObject object) {
		try {
			DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			format.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
			JDBCTemplate template = new JDBCTemplate();
			Connection con = DatabaseConnection.getConnection();
			String mailSubject = object.getString("project_name") + " | " + object.getString("testsuite_name")
					+ " | Execution Report - ";
			log.info(mailSubject);
			log.info(mailSubject);
			Timestamp timestamp = new Timestamp(System.currentTimeMillis());
			String time = format.format(timestamp);
			mailSubject += time;
			log.info(mailSubject);
			String query = "SELECT `USER_EMAIL_ID` FROM `USER_DETAILS` WHERE `USER_ID` IN( Select `USER_ID` from `PROJECT_ASSIGN_USER` where `PROJECT_ID`='"
					+ object.getString("project_id") + "')";
			System.out.println("query:::::::" + query);
			JSONArray usersarray = template.select(query, con);
			StringBuffer mailTo = new StringBuffer();
			for (int i = 0; i < usersarray.length(); i++) {
				mailTo.append(usersarray.getJSONObject(i).getString("user_email_id") + ",");

			}
			String maillist = mailTo.toString();
			maillist = maillist.substring(0, maillist.length() - 1);
			String[] mailToArray = maillist.split(",");
			String mailBody = "";

			String AlliterationTestcaseCount = "SELECT Sum(`TOTAL_TESTCASE_COUNT`) as `TOTAL_TESTCASE_COUNT`,Sum(`PASSED_TESTCASE_COUNT`) as `PASSED_TESTCASE_COUNT`,Sum(`FAILED_TESTCASE_COUNT`) as `FAILED_TESTCASE_COUNT` FROM `EXECUTION_DETAILS` WHERE PROJECT_ID ='"
					+ object.getString("project_id") + "' AND TESTSUITE_ID ='" + object.getString("testsuite_id")
					+ "' AND `EXECUTION_OVERALLSTATUS`!=''";

			JSONArray alliterationarray = template.select(AlliterationTestcaseCount, con);

			String singleiterationcount = "SELECT `TOTAL_TESTCASE_COUNT`,`PASSED_TESTCASE_COUNT`,`FAILED_TESTCASE_COUNT` FROM `EXECUTION_DETAILS` WHERE `EXECUTION_ID`='"
					+ object.getString("execution_id") + "' AND `EXECUTION_OVERALLSTATUS`!=''";

			JSONArray singleiterationarray = template.select(singleiterationcount, con);
			System.out.println("alliterationarray::::" + alliterationarray);
			System.out.println("singleiterationarray::::" + singleiterationarray);

			String totalItrtestcasecount = alliterationarray.getJSONObject(0).getString("total_testcase_count");
			String passedItrtestcasecount = alliterationarray.getJSONObject(0).getString("passed_testcase_count");
			String failedItrtestcasecount = alliterationarray.getJSONObject(0).getString("failed_testcase_count");

			String total = singleiterationarray.getJSONObject(0).getString("total_testcase_count");
			String passed = singleiterationarray.getJSONObject(0).getString("passed_testcase_count");
			String failed = singleiterationarray.getJSONObject(0).getString("failed_testcase_count");
			mailBody += "<style>"
					+ "table,tr,th,td{border:1px solid; border-collapse:collapse;text-align:center;padding:5px;}"
					+ " .charts{ margin:0 10px; }</style>";

			mailBody += "<div style='text-align:center;margin:0 auto;' align='center'>";

			mailBody += "<table style='margin:10px auto'>" + "<p>For Total Execution Iteration:</P>" + "<thead>"
					+ "<tr>" + "<th>Total Testcases</th>" + "<th>Passed Testcases</th>" + "<th>Failed Testcases</th>"
					+ "</tr>" + "</thead>" + "<tbody>";
			mailBody += "<tr>" + "<td>" + totalItrtestcasecount + "<td>" + passedItrtestcasecount + "<td>"
					+ failedItrtestcasecount + "</tr>";
			mailBody += "<div style='text-align:center;margin:0 auto;' align='center'>";

			mailBody += "<table style='margin:10px auto'>" + "<p>For Last Execution Iteration:</P>" + "<thead>" + "<tr>"
					+ "<th>Total Testcases</th>" + "<th>Passed Testcases</th>" + "<th>Failed Testcases</th>" + "</tr>"
					+ "</thead>" + "<tbody>";
			mailBody += "<tr>" + "<td>" + singleiterationarray.getJSONObject(0).getString("total_testcase_count")
					+ "<td>" + singleiterationarray.getJSONObject(0).getString("passed_testcase_count") + "<td>"
					+ singleiterationarray.getJSONObject(0).getString("failed_testcase_count") + "</tr>";

			float passPercen = Float.parseFloat(passedItrtestcasecount) / Float.parseFloat(totalItrtestcasecount) * 100;
			float failPercen = Float.parseFloat(failedItrtestcasecount) / Float.parseFloat(totalItrtestcasecount) * 100;
			float totalpercen = Float.parseFloat(totalItrtestcasecount) / Float.parseFloat(totalItrtestcasecount) * 100;

			double passPercent = Math.round(passPercen * 100.0) / 100.0;
			double failPercent = Math.round(failPercen * 100.0) / 100.0;
			double totalPercent = Math.round(totalpercen * 100.0) / 100.0;

			mailBody += "</tbody>" + "</thead>" + "</table>" + "<p>Analytics of Total Execution Records of "
					+ object.getString("project_name") + "</P>";

			mailBody += "<img class='charts' src='http://chart.apis.google.com/chart?cht=bvs&chs=400x200&chco=4169E1|378d3b|E53935&chxt=y&chxr=0,0,400&chbh=a&chtt="
					+ object.getString("project_name") + "+-+" + object.getString("testsuite_name")
					+ "+(Total,+Passed,+Failed)&chts=4B0082,16&chdl=" + total + "%28" + totalPercent + "%25%29%7C"
					+ passed + "%28" + passPercent + "%25%29%7C" + failed + "%28" + failPercent
					+ "%25%29&chl=Total|Passed|Failed&chd=t:" + total + "," + passed + "," + failed + "' />";
			mailBody += "<img src='http://chart.apis.google.com/chart?cht=p3&chs=450x200&chd=t:" + passed + "," + failed
					+ "&chdl=" + passed + "%28" + passPercent + "%25%29%7C" + failed + "%28" + failPercent
					+ "%25%29&chl=Passed|Failed&chtt=" + object.getString("project_name") + "+-+"
					+ object.getString("testsuite_name") + "+(Passed,+Failed)&chco=378d3b|E53935'>";
//					mailBody+="<img class='charts' src='http://chart.apis.google.com/chart?cht=p&chs=400x200&chdl="+passed+"%28"+passPercent+"%25%29%7C"+failed+"%28"+failPercent+"%25%29&chl=Passed%7CFailed&chco=5F9EA0|FF0000&chp=2.26889722222&chtt="+projectName+"+-+"+tsName+"+(Passed,+Failed)&chts=4B0082,16&chd=t:"+passPercent+","+failPercent+"' />";
			mailBody += "</div>";
			sendMailFromOutlook(mailToArray, mailSubject, mailBody);

		} catch (JSONException e) {
			e.printStackTrace();
			log.error("Exception caught while sending mail" + " message: " + e.getMessage());
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"Exception caught while sending mail");
		}
	}

	private void sendMailFromOutlook(String[] mailTo, String subject, String messageText) {
		Properties props = new Properties();
		log.info("Mail Executed Stared");
		try {
			
			  props.put("mail.smtp.host", "smtp.office365.com"); // SMTP Host
			  props.put("mail.smtp.port", "587"); // TLS Port 
			  props.put("mail.smtp.auth","true"); // enable authentication
			  props.put("mail.smtp.starttls.enable","true"); // enable STARTTLS
			 			
			
			
			/*
			 * Configuration config = new PropertiesConfiguration("mail.properties");
			 * props.put("mail.smtp.host",(String) config.getProperty("mail.smtp.host")); //
			 * SMTP Host props.put("mail.smtp.port",(String)
			 * config.getProperty("mail.smtp.port")); // TLS Port
			 * props.put("mail.smtp.auth",(String) config.getProperty("mail.smtp.auth")); //
			 * enable authentication props.put("mail.smtp.starttls.enable",(String)
			 * config.getProperty("mail.smtp.starttls.enable")); final String user =
			 * (String) config.getProperty("mail_user"); final String pass = (String)
			 * config.getProperty("mail_password");
			 */
			  final String user ="support@testsuiteexpress.com";
			  final String pass ="IbAutomation$1";
						 
			Session session = Session.getInstance(props, new javax.mail.Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(user, pass);
				}
			});
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(user));
			for (int i = 0; i < mailTo.length; i++) {
				message.addRecipient(Message.RecipientType.TO, new InternetAddress(mailTo[i]));
			}
			message.setSubject(subject);
			message.setText(messageText);
			message.setContent(messageText, "text/html");
			Transport.send(message);
			log.info("Mail Sent Succesfuuly");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while sending mail" + " message: " + e.getMessage());
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"Exception caught while sending mail");
		}
	}

	public JSONArray uploadtestcaseFilterInfo(JSONObject inputfromUser) {
		JSONArray jArray = new JSONArray();
		Connection con = DatabaseConnection.getConnection();
		try {
			String whereCondition = constructWhereCondition(inputfromUser.getJSONObject("customFilters"));
			String oderBy = inputfromUser.getString("order").equalsIgnoreCase("") ? ""
					: " ORDER BY " + inputfromUser.getString("order");
			int start = inputfromUser.getInt("start");
			int length = inputfromUser.getInt("length");
			String limitQuery = "";
			if (length > 0) {
				limitQuery = " LIMIT " + start + "," + length;
			}
			String mainQuery = "Select (Select COUNT(*)  FROM `PROJECT_TESTCASES` WHERE 1" + whereCondition
					+ ") AS recordsTotal, PROJECT_TESTCASES.* FROM `PROJECT_TESTCASES` WHERE 1" + whereCondition
					+ oderBy + limitQuery;
			System.out.println("MainQry:" + mainQuery);
			jArray = new JDBCTemplate().select(mainQuery, con);
		} catch (Exception e) {
			e.printStackTrace();
			log.error("SQLException caught while Applying Filters");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"SQLException caught while Applying Filters");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}
		return jArray;

	}

	private String constructWhereCondition(JSONObject jInputData) {
		String filters = "";
		try {
			Iterator<?> keys = jInputData.keys();
			while (keys.hasNext()) {
				String key = (String) keys.next();
				JSONArray values = jInputData.getJSONArray(key);
				if (values.length() != 0) {
					if (!key.equalsIgnoreCase("Testsuite_id")) {
						filters = filters + " AND `" + key + "` IN (" + constructString(values) + ")";
					} else {
						filters = filters
								+ " AND `TESTCASENAME` NOT IN (SELECT `TESTCASE_NAME` FROM  `TESECASE_MAPPER` WHERE `Testsuite_id` IN ("
								+ constructString(values) + ") )";
					}
				}

			}
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while Applying Filters");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while Applying Filters");
		}
		return filters;

	}

	public void sendMailforResetPassword(JSONArray result) {

		try {
			JDBCTemplate template = new JDBCTemplate();
			Connection con = DatabaseConnection.getConnection();
			JSONObject object = result.optJSONObject(0);
			String uniqueKey = generateBase64EncodedRandomString(9);
			String base64 = object.getString("user_id") + "_IB_" + uniqueKey;

			byte[] encoded = Base64.getEncoder().encode(base64.getBytes());

			JSONObject whereCondition = new JSONObject();

			whereCondition.put("user_id", object.getString("user_id"));
			String updateqry = "UPDATE `USER_DETAILS` SET `USER_TEMP_TIMESTAMP`= NOW(),`USER_PASSWORD_REQUEST_KEY`='"
					+ uniqueKey + "' WHERE `USER_ID`='" + object.getString("user_id") + "'";
			System.out.println("updateqry:" + updateqry);
			template.updateByQuery(updateqry, con);

			String url = "http://192.168.1.158:8005/#/forgotpassword?token=" + new String(encoded);
			System.out.println("URL::::" + url);
			String subject = "TSE Password Reset Request";
			String mailBody = "";

			mailBody = "<div style='text-align:left;margin:0 auto;' align='center'>";
			mailBody += "<p>Hi " + result.getJSONObject(0).getString("username") + " ," + "</P>";
			mailBody += "<p>&nbsp;&nbsp;&nbsp;"
					+ "Please Click below link to reset your password.Link will valid for 3 hours" + "</p>";
			mailBody += "<br>";
			mailBody += "<br>";
			mailBody += "<br>";

			mailBody += "<br><a href=" + url + ">Click Here</a>";

			mailBody += "<br>";
			mailBody += "<br>";

			mailBody += "<br><p>-----</p>";
			mailBody += "<p>Thanks & Regards</p>";
			mailBody += "<p><b>TSE Team</b></p>";

			String[] mailTo = new String[1];
			mailTo[0] = result.getJSONObject(0).getString("user_email_id");
			System.out.println("mailTomailTomailTomailTomailTo:" + mailTo[0]);

			sendMailFromOutlook(mailTo, subject, mailBody);

		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while Sending Mail");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while Sending Mail");
		}

	}

	public int verifySession(String[] decodedarray) {
		int hours = 0;
		Connection con = DatabaseConnection.getConnection();
		try {

			String qry = "SELECT hour( timediff( now(), from_unixtime( unix_timestamp(USER_TEMP_TIMESTAMP) ) ) ) as hours FROM `USER_DETAILS` WHERE `USER_PASSWORD_REQUEST_KEY` = '"
					+ decodedarray[1] + "' and USER_ID='" + decodedarray[0] + "'";

			JDBCTemplate template = new JDBCTemplate();
			JSONArray array = template.select(qry, con);
			JSONObject object = array.getJSONObject(0);
			hours = object.getInt("hours");
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while Verifying Session");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while Verifying Session");
		} finally {

			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}

		return hours;

	}

	public int updatePassword(JSONObject object) {
		Connection con = DatabaseConnection.getConnection();
		int status = 0;
		try {
			String unqiuekey = object.getString("token");
			byte[] decoded = Base64.getDecoder().decode(unqiuekey);
			String decodedtime = new String(decoded);
			System.out.println(decodedtime);
			String[] decodedarray = decodedtime.split("_IB_");
			JSONObject whereCondition = new JSONObject();
			JSONObject upDateObj = new JSONObject();
			JSONObject dataobj = new JSONObject();
			JSONArray updateDataArray = new JSONArray();

			dataobj.put("password", object.getString("password"));
			dataobj.put("user_password_request_key", "");
			dataobj.put("user_temp_timestamp", "");

			whereCondition.put("user_id", decodedarray[0]);
			upDateObj.put("filter_data", whereCondition);
			upDateObj.put("update_data", dataobj);
			updateDataArray.put(upDateObj);
			new JDBCTemplate().update("USER_DETAILS", updateDataArray, con);
			status = 1;

		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while UpdatePassword");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while UpdatePassword");
		} finally {

			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}

		return status;

	}

	public JSONArray getExecutionDetailsBasedOnExecutionId(String execution_id) {
		JSONArray executiondetails = new JSONArray();
		Connection con = DatabaseConnection.getConnection();
		JSONObject mainObject = new JSONObject();

		try {
			String query = "SELECT * FROM `EXECUTION_DETAILS_MAPPER` WHERE `EXECUTION_ID`='" + execution_id
					+ "' and `TESTCASE_RESULT`!=''";

			executiondetails = new JDBCTemplate().select(query, con);
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getting Execution Details" + " message: " + e.getMessage());
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"Exception caught while closing the connection");
		}

		finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}

		return executiondetails;

	}

	public JSONArray getTaskDashBoard(JSONObject object) {
		Connection con = DatabaseConnection.getConnection();
		try {

			String quey = "";
			String project_id = object.getString("project_id");
			String from_date = object.getString("startdate");
			String to_date = object.getString("enddate");
			JSONArray testsuiteArray = object.getJSONArray("testsuite_name");

			String whereCondition = " AND EX.`PROJECT_ID` ='" + project_id + "'";
			if (!from_date.equalsIgnoreCase("")) {
				whereCondition = whereCondition + " AND EX.`EXECUTION_TIME` >= '" + from_date + " 00:00:00'";
			}
			if (!to_date.equalsIgnoreCase("")) {
				whereCondition = whereCondition + " AND EX.`EXECUTION_TIME` <= '" + to_date + " 23:59:59'";
			}
			if (testsuiteArray.length() != 0) {
				StringBuffer testsuite = new StringBuffer();
				for (int i = 0; i < testsuiteArray.length(); i++) {
					testsuite.append("'" + testsuiteArray.getString(i) + "',");
				}
				String testsuie_name = testsuite.toString();
				testsuie_name = testsuie_name.substring(0, testsuie_name.length() - 1);
				whereCondition = whereCondition + " AND EX.TESTSUITE_NAME IN(" + testsuie_name
						+ ") ORDER BY EX.`EXECUTION_TIME` DESC";
			} else {
				whereCondition = whereCondition + " ORDER BY EX.`EXECUTION_TIME` DESC";

			}

			quey = "SELECT EX.`PROJECT_NAME`,EX.`TESTSUITE_NAME`, EX.`TESTSUITE_ID`, EX.`EXECUTION_TIME`, EX.`EXECUTION_ID`, EX.`EXECUTION_ID`,"
					+ " EM.`EXECUTION_MAPPER_ID`, EM.`TESTCASE_ID`, EM.`TESTCASE_NAME`,"
					+ " EM.`TESTCASE_RESULT`,EX.`ITERATION_NUMBER`,EM.`TESTCASE_DURATION`  FROM `EXECUTION_DETAILS` AS EX, `EXECUTION_DETAILS_MAPPER` AS EM WHERE EX.`EXECUTION_ID` = EM.`EXECUTION_ID` AND EX.`PROJECT_ID` = EM.`PROJECT_ID`"
					+ whereCondition;
			System.out.println("quey:queyqueyquey" + quey);

			JSONArray excuitionArray = new JSONArray();
			int project_level_test_case_count = 0;
			int project_level_pass_test_case_count = 0;
			int project_level_fail_test_case_count = 0;

			// String quey = "SELECT EX.`PROJECT_NAME`,EX.`TESTSUITE_NAME`,
			// EX.`TESTSUITE_ID`, EX.`EXECUTION_TIME`, EX.`EXECUTION_ID`, EX.`EXECUTION_ID`,
			// EM.`EXECUTION_MAPPER_ID`, EM.`TESTCASE_ID`, EM.`TESTCASE_NAME`,
			// EM.`TESTCASE_RESULT`,EX.`ITERATION_NUMBER`,EM.`TESTCASE_DURATION` FROM
			// `EXECUTION_DETAILS` AS EX, `EXECUTION_DETAILS_MAPPER` AS EM WHERE
			// EX.`EXECUTION_ID` = EM.`EXECUTION_ID` AND EX.`PROJECT_ID` = EM.`PROJECT_ID`
			// AND EX.`PROJECT_ID` ='12' AND EX.`EXECUTION_TIME` >= '2019-03-31 00:00:00'
			// AND EX.`EXECUTION_TIME` <= '2019-04-04 23:59:59' ORDER BY EX.`EXECUTION_TIME`
			// ASC";
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			JSONArray excutionData = jdbcTemplate.select(quey, con);
			JSONObject excuitionLevelData = new JSONObject();
			JSONArray OrderOfExcuitionLevelData = new JSONArray();
			for (int i = 0; i < excutionData.length(); i++) {
				JSONObject excutionObj = excutionData.getJSONObject(i);
				String testcase_result = excutionObj.getString("testcase_result");
				String execution_id = excutionObj.getString("execution_id");

				int pass = 0;
				int fail = 0;
				if (testcase_result.equalsIgnoreCase("PASSED")) {
					pass = 1;
				} else if (testcase_result.equalsIgnoreCase("Failed")) {
					fail = 1;
				}

				project_level_test_case_count = project_level_test_case_count + 1;
				project_level_pass_test_case_count = project_level_pass_test_case_count + pass;
				project_level_fail_test_case_count = project_level_fail_test_case_count + fail;
				JSONObject excution_obj = null;
				JSONArray testDataCaseArray = null;

				if (!excuitionLevelData.has(execution_id)) {
					excution_obj = new JSONObject();
					excution_obj.put("testsuite_name", excutionObj.getString("testsuite_name"));
					excution_obj.put("project_name", excutionObj.getString("project_name"));
					excution_obj.put("iteration_number", excutionObj.getString("iteration_number"));
					excution_obj.put("execution_time", excutionObj.getString("execution_time"));
					excution_obj.put("total_testcase_count", String.valueOf(1));
					excution_obj.put("passed_testcase_count", String.valueOf(pass));
					excution_obj.put("failed_testcase_count", String.valueOf(fail));
					excution_obj.put("execution_id", execution_id);
					excution_obj.put("testcase_name", excutionObj.getString("testcase_name"));
					excution_obj.put("testcasedata", testDataCaseArray);
					OrderOfExcuitionLevelData.put(execution_id);
					excuitionLevelData.put(execution_id, excution_obj);
				} else {
					excution_obj = excuitionLevelData.getJSONObject(execution_id);
					excution_obj.put("total_testcase_count",
							String.valueOf(Integer.parseInt(excution_obj.getString("total_testcase_count")) + 1));
					excution_obj.put("passed_testcase_count",
							String.valueOf(Integer.parseInt(excution_obj.getString("passed_testcase_count")) + pass));
					excution_obj.put("failed_testcase_count",
							String.valueOf(Integer.parseInt(excution_obj.getString("failed_testcase_count")) + fail));
					excution_obj.put("iteration_count", "0");
					// excution_obj.put("testcase_array",excution_obj.getJSONArray("testcase_array").put(excution_obj.getString("testcase_name")));

				}
			}
			for (int j = 0; j < OrderOfExcuitionLevelData.length(); j++) {
				String excuitionId = OrderOfExcuitionLevelData.getString(j);
				JSONObject excuitionObj = excuitionLevelData.getJSONObject(excuitionId);
				if (excuitionObj.getString("execution_id").equalsIgnoreCase(excuitionId)) {
					excuitionArray.put(excuitionObj);
				}
			}
			System.out.println(excuitionArray);
			return excuitionArray;
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while getTaskDashBoard");
			throw new TSENestableException(TSENestableException.CODE_AUTHENTICATION_ERROR,
					"JSONException caught while getTaskDashBoard");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}
		}

	}

	public String ValidateScriptStatus(JSONObject object) {
		JSONArray executiondetails = new JSONArray();
		Connection con = DatabaseConnection.getConnection();
		JSONObject mainObject = new JSONObject();
		String resource = "Free";

		try {
			String query = "SELECT `EXECUTION_ID` FROM `TARGET_REMOTE_MACHINES` WHERE `TARGET_REMOTE_MACHINE_ID`='"
					+ object.getString("target_remote_machine_id") + "'";

			executiondetails = new JDBCTemplate().select(query, con);
			if (!executiondetails.getJSONObject(0).getString("execution_id").equalsIgnoreCase("")) {
				resource = "Busy";
			} else {
				resource = "Free";
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Exception caught while getting Execution Details" + " message: " + e.getMessage());
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"Exception caught while closing the connection");
		}

		finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}

		return resource;

	}

	public boolean insertGitDetails(JSONObject object) {
		boolean status = false;
		Connection con = DatabaseConnection.getConnection();

		try {

			// String status=new Git_Integarte().validate(object);
			status = new Git_Integarte().validateRepository(object.getString("git_url"),
					object.getString("git_username"), object.getString("git_password"));
			if (status) {
				JSONObject whereCondition = new JSONObject();
				JSONObject upDateObj = new JSONObject();
				JSONArray updateDataArray = new JSONArray();
				JDBCTemplate template = new JDBCTemplate();
				con.setAutoCommit(false);
				whereCondition.put("project_id", object.getString("project_id"));
				object.remove("project_id");
				object.remove("project_name");
				upDateObj.put("filter_data", whereCondition);
				upDateObj.put("update_data", object);
				updateDataArray.put(upDateObj);
				template.update("PROJECTS_INFO", updateDataArray, con);
				con.commit();
			}
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("Exception caught while inserting git details" + " message: " + e.getMessage());
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"Exception caught while inserting git details");
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("Exception caught while inserting git details" + " message: " + e.getMessage());
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"Exception caught while inserting git details");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}

		return status;
	}

	public String ExecuteManualTestcases(JSONObject object1) {
		JSONObject tempJsonObject = object1;
		Connection con = DatabaseConnection.getConnection();
		String executionId = "";
		try {
			con.setAutoCommit(false);
			executionId = String.valueOf(insertexecuteTestSuiteData(tempJsonObject, con));
			JSONArray array = object1.getJSONArray("input_data");
			for (int j = 0; j < array.length(); j++) {

				JSONObject object = array.getJSONObject(j);
				String project_id = object.getString("project_id");
				String testsuite_id = object.getString("testsuite_id");
				String testsuite_name = object.getString("testsuite_name");
				String project_name = object.getString("project_name");
				insertExecutionInfoformanualTestcases(object, executionId, project_name, project_id, testsuite_id,
						testsuite_name,con);
			}
			con.commit();
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while ExecutngManualTestcases" + " message: " + e.getMessage());
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"Exception caught while ExecutngManualTestcases");
		} catch (SQLException e) {
			e.printStackTrace();
			log.error("SQLException caught while ExecutngManualTestcases" + " message: " + e.getMessage());
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"Exception caught while ExecutngManualTestcases");
		} finally {
			try {
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}

		System.out.println("ExecutionId:" + executionId);
		return executionId;
	}

	private int insertExecutionInfoformanualTestcases(JSONObject object, String executionId, String project_name,
			String project_id, String testsuite_id, String testsuite_name,Connection con) {
		int status=0;
		try {
			JDBCTemplate jdbcTemplate = new JDBCTemplate();
			JSONArray insertstepsArray = new JSONArray();
			JSONObject indiviualtestcaseresultobj = new JSONObject();
			JSONArray testcasearray = object.getJSONArray("testcases");
			for (int i = 0; i < testcasearray.length(); i++) {
				JSONObject testcaseoObject = testcasearray.getJSONObject(i);
				String testcasename = testcaseoObject.getString("testcasename");
				JSONArray stepsarray = testcaseoObject.getJSONArray("steps");
				int failstep=0;
				for (int k = 0; k < stepsarray.length(); k++) {
					JSONObject indiviualstepobject = stepsarray.getJSONObject(k);
					if(indiviualstepobject.has("$$hashKey"))
					{
						indiviualstepobject.remove("$$hashKey");
					}
					indiviualstepobject.put("testsuite_id", testsuite_id);
					indiviualstepobject.put("execution_id", executionId);
					indiviualstepobject.put("image_name", executionId+ "_" + testcasename + "_"
							+ indiviualstepobject.getString("teststep_number") + ".png");
					indiviualstepobject.put("image_path", "");
					indiviualstepobject.put("image_format", "");
					indiviualstepobject.put("testsuite_name", testsuite_name);
					indiviualstepobject.put("testcase_name", testcasename);
					indiviualstepobject.put("project_name", project_name);
					indiviualstepobject.put("project_id", project_id);
					String stepresult = indiviualstepobject.getString("stepresult");
					if (stepresult.equalsIgnoreCase("Passed")) {
						stepresult="Passed";

					} else if (stepresult.equalsIgnoreCase("Failed")) {
						stepresult="Failed";
						failstep=1;
					}
					indiviualstepobject.remove("test_step");

					insertstepsArray.put(indiviualstepobject);
				
				}
				if(failstep>0)
				{
				indiviualtestcaseresultobj.put(testcasename,"Failed");
				}else {
					indiviualtestcaseresultobj.put(testcasename,"Passed");

				}


			}
			System.out.println("insertstepsArray:"+insertstepsArray);
			jdbcTemplate.insert("TESECASE_STEPS_RESULTS_MAPPER", insertstepsArray, con);
			if (indiviualtestcaseresultobj.length() != 0) {
				Iterator<String> testcasename = indiviualtestcaseresultobj.keys();
				JSONArray updateDataArray = new JSONArray();
				int passed = 0;
				int failed = 0;
				int skipped = 0;
				while (testcasename.hasNext()) {
					JSONObject whereCondition = new JSONObject();
					JSONObject upDateObj = new JSONObject();
					JSONObject updateresult = new JSONObject();
					String testcase_name=testcasename.next();
					whereCondition.put("execution_id",executionId);
					whereCondition.put("testcase_name",testcase_name );
					String testcase_result = indiviualtestcaseresultobj.getString(testcase_name);
					updateresult.put("testcase_result", testcase_result);
					if (testcase_result.equalsIgnoreCase("Passed")) {
						passed = passed + 1;
					} else if (testcase_result.equalsIgnoreCase("Failed")) {
						failed = failed + 1;
					} else if (testcase_result.equalsIgnoreCase("Skipped")) {
						skipped = skipped + 1;

					}
					upDateObj.put("filter_data", whereCondition);
					upDateObj.put("update_data", updateresult);
					updateDataArray.put(upDateObj);
				}
				System.out.println("updateDataArray:"+updateDataArray);

				jdbcTemplate.update("EXECUTION_DETAILS_MAPPER", updateDataArray, con);

				JSONObject whereCondition = new JSONObject();
				JSONObject upDateObj = new JSONObject();
				JSONObject updateresult = new JSONObject();
				whereCondition.put("execution_id", executionId);
				updateresult.put("total_testcase_count", String.valueOf(passed + failed + skipped));
				updateresult.put("passed_testcase_count",String.valueOf(passed));
				updateresult.put("failed_testcase_count",String.valueOf(failed));
				updateresult.put("skipped_testcase_count",String.valueOf(skipped));
				if (failed > 0) {
					updateresult.put("EXECUTION_OVERALLSTATUS", "Failed");

				} else if (passed > 0) {
					updateresult.put("EXECUTION_OVERALLSTATUS", "Passed");

				} else if (skipped > 0) {
					updateresult.put("EXECUTION_OVERALLSTATUS", "Skipped");

				}
				upDateObj.put("filter_data", whereCondition);
				upDateObj.put("update_data", updateresult);
				updateDataArray.put(upDateObj);
				System.out.println("EXECUTION_DETAILS:"+updateDataArray);
				jdbcTemplate.update("EXECUTION_DETAILS", updateDataArray, con);
			}

			status=1;
		} catch (JSONException e) {
			e.printStackTrace();
			log.error("JSONException caught while ExecutngManualTestcases" + " message: " + e.getMessage());
			throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
					"Exception caught while ExecutngManualTestcases");
		} 
		return status;
	}

	public JSONArray readTestSuiteExecutionStatus(String execution_id) {
		JSONArray jsonArray = new JSONArray();
		Connection connection = DatabaseConnection.getConnection();
		try {
			JDBCTemplate template = new JDBCTemplate();
			String query = "SELECT `EXECUTION_OVERALLSTATUS` FROM `EXECUTION_DETAILS` WHERE `EXECUTION_ID`='"
					+ execution_id + "'";
			System.out.println("query:" + query);
			jsonArray = template.select(query, connection);
			
		} finally {
			try {
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Exception caught while closing the connection" + " message: " + e.getMessage());
				throw new TSENestableException(TSENestableException.CODE_INTERNAL_SERVER_ERROR,
						"Exception caught while closing the connection");
			}

		}

		return jsonArray;
	}

}