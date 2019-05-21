package main; 
import org.json.JSONException;

import testsuite.Bioscience;
 import util.IdeabytesTestRunner;
public  class TestSuiteRunner  {
	public static void main(String[] args) throws JSONException  {
IdeabytesTestRunner.runTestCases();
Bioscience obj = new Bioscience();
	}
}
