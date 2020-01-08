//
// Copyright (c) 2016 Advantest. All rights reserved.
//
// Contributors:
//  Advantest - initial implementation
//
// NOTICE: ADVANTEST PROVIDES THIS SOFTWARE TO YOU ONLY UPON YOUR ACCEPTANCE OF ADVANTEST'S
// TERMS OF USE. USE OF THIS SAMPLE SOURCE CODE IS GOVERNED BY AND SUBJECT TO THE ADVANTEST
// LICENSE AND SERVICE AGREEMENT FOR APPLICATION SOFTWARE AND SERVICES AND IS PROVIDED "AS-IS",
// WITHOUT WARRANTY OF ANY KIND, UNLESS USE OF SUCH SAMPLE SOURCE CODE IS OTHERWISE EXPRESSLY
// GOVERNED BY AN AGREEMENT SIGNED BY ADVANTEST.
//

/**  Functional Char method with Vmin, Fmax,ErrorLog,ScanLog options
     Please have the reports/scan created in your deveopement.


suite FN_MBURST_tk_atpg_int_lpu_top_AON_XMD_SVS calls COMMON.testmethods.digital.FunctionalCharTest{

     FuncChar [Vmin] = {
      Start = 1.4;
      Stop = 0.3;
      step = "-0.01";
      resolution = "0.005";
      SearchMethod = LINBIN;
      SpecVar = "vdd_mx";
      };



      FuncChar [Fmax] = {
      Start = 5;
      Stop = 200;
      step = "5";
      resolution = "0.5";
      SearchMethod = LINBIN;
      SpecVar = "Frequency1";
      };

      burstMode = true;
      scanSignals = "SCAN_OUT";
      Debug.state=4;
  measurement.operatingSequence = setupRef(r1_secn10lpe.TDF_ATPG.MBURST_tk_atpg_tdf_lpc_a7ihmF37F64_120_S_XMD);
  measurement.specification = setupRef(ATPG.specs.AON__SVS_SS_NoTermination__10Mhz);
}

*/

package msmsoctml.digital;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import msmsoctml.ac.SpecSearchParametersUtilities.SearchMethodOption;
import msmsoctml.ac.SpecSearchTask;
import msmsoctml.common.MultiSiteValUtil;
import soctml.base.SOCTestBase;
import soctml.common.FileUtilities;
import soctml.common.StringUtilities;
import soctml.common.SynchronizedVariables.SyncMultiSiteDouble;
import soctml.common.SynchronizedVariables.SyncMultiSiteLong;
import soctml.exceptions.SocException;
import xoc.dta.ParameterGroup;
import xoc.dta.ParameterGroupCollection;
import xoc.dta.UncheckedDTAException;
import xoc.dta.annotations.In;
import xoc.dta.annotations.Out;
import xoc.dta.datatypes.MultiSiteBoolean;
import xoc.dta.datatypes.MultiSiteDouble;
import xoc.dta.datatypes.MultiSiteLong;
import xoc.dta.datatypes.MultiSiteLongArray;
import xoc.dta.instrument.IDigInOut;
import xoc.dta.measurement.IMeasurement;
import xoc.dta.resultaccess.IDigInOutCallPassFailResults;
import xoc.dta.resultaccess.IDigInOutCaptureResults;
import xoc.dta.resultaccess.IDigInOutCyclePassFailResults;
import xoc.dta.resultaccess.IDigInOutResults;
import xoc.dta.resultaccess.IDigInOutSummaryResults;
import xoc.dta.resultaccess.IMeasurementResult;
import xoc.dta.resultaccess.IPassFail;
import xoc.dta.resultaccess.IResultPatternPassFail;
import xoc.dta.resultaccess.datatypes.BitSequence;
import xoc.dta.resultaccess.datatypes.FailedCycle;
import xoc.dta.resultaccess.datatypes.MultiSiteBitSequence;
import xoc.dta.resultaccess.datatypes.MultiSiteFailedCycleArray;
import xoc.dta.setupaccess.IParallelGroup;
import xoc.dta.setupaccess.IPatternCall;
import xoc.dta.signals.ISignal;
import xoc.dta.testdescriptor.IFunctionalTestDescriptor;
import xoc.dta.testdescriptor.IParametricTestDescriptor;
import xoc.dta.testdescriptor.IScanTestDescriptor;
import xoc.dta.workspace.IWorkspace;

//@SuppressWarnings("restriction")
//@SuppressWarnings("restriction")
public abstract class FunctionalCharBase extends SOCTestBase
{
    // Parameters used in ST7, may be considered in ST8 too
    //    @In public int StopCycle = -1; // not supported yet
    //    @In public int StopVector = -1; // not supported yet
    //    @In public int PortNameStopCycle = -1; // no Port in ST8
    // mAllowHiddenUpload(NO_STR) - need to find out how to implement
    // mCheckDPSStatus(false) - need to find out how to implement

    //Added 21/02/2019
    public IParametricTestDescriptor Parametric_Vmin_Margin;
    public IParametricTestDescriptor Parametric_Vmin_Target;

    @In public boolean VminMarginFlag = false;
    @In public String Var_VminTarget="";
    @In public String totalErrCountVariable ="";
    @In public double DBL_VminTarget = 0;



    /** functional test descriptor for datalog */
    public IFunctionalTestDescriptor Functional;
    public IFunctionalTestDescriptor Functional_VecDumpFail;
    /** scan test descriptor for datalog */
    public IScanTestDescriptor ScanTestSTR;

    @In public boolean TestResults = true;
    @In public boolean VminFlag = false;
    @In public boolean FmaxFlag = false;

    @Out public MultiSiteDouble VminResult, FmaxResult;
    public IParametricTestDescriptor Parametric;
    public IParametricTestDescriptor Parametric_Vmin;
    public IParametricTestDescriptor Parametric_Fmax;

    @In public boolean ErrorLog = false;
    @In public boolean DigCap = false;

    //BURST mode scan logging

    @In public boolean ScanErrorLog = false;
    @In public boolean burstMode = false;
    // check if at least one site has failed
    protected boolean allPassed = true;
    protected IMeasurementResult measurementResult    = null;
    protected IDigInOutResults   digInOutResults      = null;
    protected IDigInOutResults   digInOutResultsFTR      = null;
    protected IResultPatternPassFail[] callResult;
    protected IResultPatternPassFail[] resultPerPatArray;
    //public IScanTestDescriptor       ScanTestSTR; // STR
    public IFunctionalTestDescriptor ScanTestFTR; // FTR
    protected List<String> allParallelGroup;
    String testText_FTR = "ScanTestFTR";
    String testText_STR = "ScanTestSTR";
    int baseFTRTestNumber;
    int baseSTRTestNumber;
    /** how to report to file*/
    @In public String output = "None"; // None,File,ReportUI,File+ReportUI
    /** include expected data or not*/
    @In public int includeExpectedData = 0;

    @In public int maxFailsPerPin = 5000;

    /** signal names that will be datalogged into STR record */
    @In public String scanSignals = "";

    /** Lin/Bin search start */
    @In public double Start = 0.0;

    /** Lin/Bin search  stop */
    @In public double Stop = 0.0;

    /** Step width in Lin/Bin search. If preceded with #, it will be processed as number of steps */
    @In public String Step = "";

    /** Resolution in binary search*/
    @In public String Resolution = "";

    /** Pin List to perform SpecSearch */
    @In public String SetupPinlist = ""; // Does SMT8 support individual pins?

    /** SearchMethod enum, options are: LINEAR : BINARY : LINBIN */
    @In public SearchMethodOption SearchMethod  = SearchMethodOption.LINEAR; // Linear Search, Binary Search or Linear-Binary Search

    /** Eg. Frequency_1 or vdd_q6, etc */
    @In public String SpecVariableName  = "";

    /** Flag to do VecDump for functional fail only */
    @In public boolean VecDumpFailOnly = false;

    /** Flag to do getTotalErrorCount for functional fail only */
    protected boolean setTotalErrorCount = false;



    /** Parameters for VecDump together with shmoo to record shmoo condition into the name of dump file*/
    @In public String VddSpecVar_shmoo = "";
    @In public String FreqSpecVar_shmoo = "";

    //TODO: We don't support change spec prior retest
    //    @In public String  RetestSpec = "";

    /** main object for the test execution */
    public IMeasurement measurement;
    public IMeasurement measurement_Functional;

    /* protected members */
    protected final int        LOGLEVEL_PF_ONLY  = 10;
    protected final int        LOGLEVEL_SCAN     = 30;
    protected final int        LOGLEVEL_EXPECTED = 40;
    protected IPassFail        funcResults      = null;
    protected boolean          retestEnabled    = false;  // is a retest specified in the testflow and global retest switch is on ?
    protected boolean          retestDone       = false;  // has a retest been done ?
    protected MultiSiteBoolean pf = null;
    protected MultiSiteBoolean pf_VecDumpFail = null;

    protected SpecSearchTask vmintask, fmaxtask;

    protected IDigInOutResults results                = null;

    protected boolean          scanDataloggingEnabled = true;
//    protected boolean          expectedDataLogging    = true;
    protected boolean          expectedDataLogging    = false;
    protected long             maxFailCycles          = 5000;

    protected String vmin_spec ="";
    protected double vmin_start =0.0;
    protected double vmin_stop = 0.0;
    protected SearchMethodOption vmin_opt = SearchMethodOption.LINEAR;
    protected String vmin_step ="";
    protected String vmin_resolution = "";

    protected String fmax_spec ="";
    protected double fmax_start =0.0;
    protected double fmax_stop = 0.0;
    protected SearchMethodOption fmax_opt = SearchMethodOption.LINEAR;
    protected String fmax_step ="";
    protected String fmax_resolution = "";

    /** digCap pinList, MSB first, LSB last */
    @In public String       digCapPinList= ""; // + separated value list of the capture pins (there's no digcap vectorvar anymore obviously)
    /** number of captures */
    @In public int   numCaptures = 0;

    protected MultiSiteLongArray digCap_msl = new MultiSiteLongArray();

    protected int  rowsToProcess = 0;   // this will be how many are actually processed

    protected  int rowsOfCapture = 0;   // this will be how many are actually detected  during the measurement run

    protected long totalErrorCount = 0;
    protected MultiSiteLong MStotalErrorCount = new MultiSiteLong(0);


    public ParameterGroupCollection<FuncCharInfo> FuncChar = new ParameterGroupCollection<>();

    public static class FuncCharInfo extends ParameterGroup {

        public double     Start = 0.0 ;
        public double     Stop  = 0.0 ;
        public String     VecDumppins   ="";
        public String     ErrorLogPins  ="";
        public SearchMethodOption   SearchMethod = SearchMethodOption.LINEAR ;
        public String SpecVar = "";
        public String step = "";
        public String resolution = "";
        public long maxFails = 0;
        public int captures = 0;
        public boolean getTotalErrorCount = false;
    }

    @Override
    public void generateSpecs() {
        measurement_Functional.setOperatingSequenceName(measurement.getOperatingSequenceName());

    //    if (!VecDumpFailOnly)
    //    {
            measurement_Functional.setSpecificationName(measurement.getSpecificationName());
   //     }

        if(FuncChar.size()>3) {
            throw new UncheckedDTAException("Only max 3 Setups allowed\n Choose Vmin+Fmax+ErrorLog/VecDump only\n");
        }

        for (FuncCharInfo funcchar : FuncChar.values()){

            if(funcchar.getId().equals("Vmin") ) {
                VminFlag = true;
                vmin_spec =funcchar.SpecVar;
                vmin_start = funcchar.Start;
                vmin_stop = funcchar.Stop;
                vmin_opt = funcchar.SearchMethod;
                vmin_resolution = funcchar.resolution;
                vmin_step = funcchar.step;
            }
            else if (funcchar.getId().equals("Fmax") ) {
                FmaxFlag = true;
                fmax_spec =funcchar.SpecVar;
                fmax_start = funcchar.Start;
                fmax_stop = funcchar.Stop;
                fmax_opt = funcchar.SearchMethod;
                fmax_resolution = funcchar.resolution;
                fmax_step = funcchar.step;
            }
            else if (funcchar.getId().equals("ErrorLog") ) {
                ErrorLog = true;

                scanSignals = funcchar.ErrorLogPins;
                maxFailCycles = funcchar.maxFails;
                setTotalErrorCount = funcchar.getTotalErrorCount;

            }
            else if (funcchar.getId().equals("ScanErrorLog") ) {
                if(ErrorLog == true){
                    throw new UncheckedDTAException("Only ErrroLog or VecDump is allowed in one TM \n");
                }
                ScanErrorLog = true;
                scanSignals = funcchar.ErrorLogPins;
                maxFailCycles = funcchar.maxFails;

            }
            else if (funcchar.getId().equals("VecDump") ) {
                if(ErrorLog == true || ScanErrorLog == true ){
                    throw new UncheckedDTAException("Only ErrroLog or VecDump is allowed in one TM \n");
                }

                DigCap = true;
                digCapPinList = funcchar.VecDumppins;
                numCaptures = funcchar.captures;
            }
            else{
                throw new UncheckedDTAException("Unknown setup, please use Vmin/Fmax/ErrorLog/VecDump");
            }

            if(Debug.showUser2()) {
                System.out.println("  Setup "       +   funcchar.getId());
                System.out.println("  Spec  "       +   funcchar.SpecVar);
                System.out.println("  VecDump Pins "+   funcchar.VecDumppins);
                System.out.println("  Error Pins   "+   funcchar.ErrorLogPins);
                System.out.println("  Start  "       +  funcchar.Start);
                System.out.println("  Stop  "       +   funcchar.Stop);
                System.out.println("  Step  "       +   funcchar.step);
                System.out.println("  Method  "       + funcchar.SearchMethod);
                System.out.println("  Num of cap "    +   funcchar.captures);
                System.out.println("  Max fails  "    +   funcchar.maxFails);
            }
        }

        if (burstMode == true && ScanErrorLog==false)
        {
            if(ErrorLog == true){
                throw new UncheckedDTAException("Only ErrroLog or VecDump is allowed in one TM \n");
            }
            ScanErrorLog = true;
            maxFailCycles = maxFailsPerPin;
        }
    }

    @Override
    public void update() {
        super.update();

        // set TEST_TXT to <testsuite name>:<test text from testtable>
        // store testsuite name for datalogging
        String[] tmpts = context.getTestSuiteName().split("\\.");
        String testsuiteName = tmpts.length>0 ? tmpts[tmpts.length-1] : context.getTestSuiteName();

        //        if(scanDataloggingEnabled ) {
        //            measurement.digInOut(scanSignals).result().cyclePassFail().setEnabled(true);
        //            measurement.digInOut(scanSignals).result().cyclePassFail().setMaxFailedCycles(maxFailCycles);
        //            measurement.digInOut(scanSignals).result().callPassFail().setEnabled(true);
        //        }
        //        else {
        //            measurement.digInOut(scanSignals).result().cyclePassFail().setEnabled(false);
        //            measurement.digInOut(scanSignals).result().cyclePassFail().setMaxFailedCycles(0);
        //            measurement.digInOut(scanSignals).result().callPassFail().setEnabled(false);
        //        }

        if(ErrorLog){
            measurement.digInOut(scanSignals).result().cyclePassFail().setEnabled(true);
            measurement.digInOut(scanSignals).result().cyclePassFail().setMaxFailedCycles(maxFailCycles);
            measurement.digInOut(scanSignals).result().callPassFail().setEnabled(true);
        }
        if(ScanErrorLog){
            measurement.digInOut(scanSignals).result().cyclePassFail().setEnabled(true);
            measurement.digInOut(scanSignals).result().cyclePassFail().setMaxFailedCycles(maxFailCycles);
            measurement.digInOut(scanSignals).result().callPassFail().setEnabled(true);

            testText_FTR = ScanTestFTR.getTestText();
            testText_STR = ScanTestSTR.getTestText();

            baseFTRTestNumber = ScanTestFTR.getTestNumber();
            baseSTRTestNumber = ScanTestSTR.getTestNumber();

            allParallelGroup = getParallelGroupName(measurement);

            if(burstMode ) {
                allParallelGroup = ScanUtility.getParallelGroupName(measurement);
            }

        }
        else if (DigCap){
            measurement.digInOut(digCapPinList).result().capture().setEnabled(true);
        }

        Functional.setTestText(testsuiteName+":"+Functional.getTestText());
    }

    @Override
    public void configure()
    {
        //initialize variable
        pf = new MultiSiteBoolean(false);

        if(VminFlag )
        {
            vmintask = new SpecSearchTask(measurement, /*context, */this, Debug.showUser());
            vmintask.setupPin(SetupPinlist).spec(vmin_spec).method(vmin_opt).start(vmin_start).stop(vmin_stop);

            if(vmin_resolution.trim().isEmpty())
            {
                if(vmin_opt.equals(SearchMethodOption.BINARY) || vmin_opt.equals(SearchMethodOption.LINBIN))
                {
                    throw new SocException(this, "Resolution can not be empty when using binary or lin/bin search");
                }
            }
            else
            {
                try
                {
                    vmintask.resolution(Double.parseDouble(vmin_resolution));
                }
                catch (NumberFormatException e)
                {
                    throw new SocException(this, "The Resolution value " + Resolution + " can not be parsed into Double type. Please redefine Step value.");
                }
            }

            if(vmin_step.trim().isEmpty())
            {
                if(SearchMethod.equals(SearchMethodOption.LINEAR) || SearchMethod.equals(SearchMethodOption.LINBIN))
                {
                    throw new SocException(this, "Step can not be empty when using linear or lin/bin search");
                }
            }
            else
            {
                //_step has value
                if(vmin_step.trim().startsWith("#"))
                {
                    int stepNumber = -999;

                    try
                    {
                        stepNumber = Integer.parseInt(vmin_step.substring(1));
                    }
                    catch (NumberFormatException e)
                    {
                        throw new SocException(this, "The Step value " + vmin_step.substring(1) + " can not be parsed into Integer type. Please redefine Step value.");
                    }

                    vmintask.stepWidth((vmin_stop - vmin_start)/ (stepNumber - 1));
                }
                else
                {
                    try
                    {
                        vmintask.stepWidth(Double.parseDouble(vmin_step));
                    }
                    catch (NumberFormatException e)
                    {
                        throw new SocException(this, "The Step value " + Step + " can not be parsed into Double type. Please redefine Step value.");
                    }

                    if ((vmin_start >= vmin_stop) && (Double.parseDouble(vmin_step) >= 0))
                    {
                        throw new SocException(this, "Step value should be negative value, when start value (" + Start + ") is equal or greater than stop value (" + Stop + ")\n Please reprogram your start/stop/step value");
                    }

                    if ((vmin_start <= vmin_stop) && (Double.parseDouble(vmin_step) <= 0 ))
                    {
                        throw new SocException(this, "Step value should be positive value, when start value (" + Start + ") is equal or lesser than stop value (" + Stop + ")\n Please reprogram your start/stop/step value");
                    }
                }
            }//end of setting step
        }

        if(FmaxFlag )
        {
            fmaxtask = new SpecSearchTask(measurement, /*context, */this, Debug.showUser());
            fmaxtask.setupPin(SetupPinlist).spec(fmax_spec).method(fmax_opt).start(fmax_start).stop(fmax_stop);

            if(fmax_resolution.trim().isEmpty())
            {
                if(fmax_opt.equals(SearchMethodOption.BINARY) ||fmax_opt.equals(SearchMethodOption.LINBIN))
                {
                    throw new SocException(this, "Resolution can not be empty when using binary or lin/bin search");
                }
            }
            else
            {
                try
                {
                    fmaxtask.resolution(Double.parseDouble(fmax_resolution));
                }
                catch (NumberFormatException e)
                {
                    throw new SocException(this, "The Resolution value " + Resolution + " can not be parsed into Double type. Please redefine Step value.");
                }
            }

            if(fmax_step.trim().isEmpty())
            {
                if(fmax_opt.equals(SearchMethodOption.LINEAR) || fmax_opt.equals(SearchMethodOption.LINBIN))
                {
                    throw new SocException(this, "Step can not be empty when using linear or lin/bin search");
                }
            }
            else
            {
                //_step has value
                if(fmax_step.trim().startsWith("#"))
                {
                    int stepNumber = -999;

                    try
                    {
                        stepNumber = Integer.parseInt(fmax_step.substring(1));
                    }
                    catch (NumberFormatException e)
                    {
                        throw new SocException(this, "The Step value " + fmax_step.substring(1) + " can not be parsed into Integer type. Please redefine Step value.");
                    }

                    fmaxtask.stepWidth((fmax_stop - fmax_start)/ (stepNumber - 1));
                }
                else
                {
                    try
                    {
                        fmaxtask.stepWidth(Double.parseDouble(fmax_step));
                    }
                    catch (NumberFormatException e)
                    {
                        throw new SocException(this, "The Step value " + Step + " can not be parsed into Double type. Please redefine Step value.");
                    }

                    if ((fmax_start >= fmax_stop) && (Double.parseDouble(fmax_step) >= 0))
                    {
                        throw new SocException(this, "Step value should be negative value, when start value (" + Start + ") is equal or greater than stop value (" + Stop + ")\n Please reprogram your start/stop/step value");
                    }

                    if ((fmax_start <= fmax_stop) && (Double.parseDouble(fmax_step) <= 0 ))
                    {
                        throw new SocException(this, "Step value should be positive value, when start value (" + Start + ") is equal or lesser than stop value (" + Stop + ")\n Please reprogram your start/stop/step value");
                    }
                }
            }//end of setting step
        }
    }

    @Override
    public void runMeasurement()
    {
        funcResults = null;

        // Vmin or Fmax search
        if(VminFlag )
        {
            scanDataloggingEnabled = false;
            vmintask.execute(context);
        }
        if(FmaxFlag){
            scanDataloggingEnabled = false;
            fmaxtask.execute(context);
        }

        // capture error map
        if (ErrorLog )
        {
            // assign default pins if no scan signals are specified
            if (scanSignals.trim().isEmpty()) {
                scanSignals = "SCAN_OUT";
            }

            // turn scan datalogging on/off depending on TP Var
            if(scanDataloggingEnabled) {
                if(expectedDataLogging) {
                    ScanTestSTR.setLogLevel(LOGLEVEL_EXPECTED);
                }
                else {
                    ScanTestSTR.setLogLevel(LOGLEVEL_SCAN);
                }

                if(Debug.showUser()) {
                    System.out.println("Scan datalogging enabled. Loglevel="+ScanTestSTR.getLogLevel());
                }
            }
            else {
                ScanTestSTR.setLogLevel(LOGLEVEL_PF_ONLY);
                if(Debug.showUser()) {
                    System.out.println("Scan datalogging disabled. Loglevel="+ScanTestSTR.getLogLevel());
                }
            }

            // run measurement
            measurement.execute();






            // obtain running result (upload error map)
            IDigInOut dioScanOut = measurement.digInOut(scanSignals);
            MultiSiteBoolean funcRslt = measurement.hasPassed();

            if (isOffline) {
                funcRslt = new MultiSiteBoolean(false);
            }
            results = dioScanOut.preserveResults(ScanTestSTR);
            pf = funcRslt;
            boolean hasFail = MultiSiteValUtil.has(funcRslt, false);

            Map<String,MultiSiteFailedCycleArray> failCycles = null;
            if ((hasFail && Debug.showUser()) ||(hasFail && Debug.showGraphOrSaveData()))  {
                IDigInOutCyclePassFailResults cyclRslt = dioScanOut.preserveCyclePassFailResults();
                failCycles = cyclRslt.getFailedCycleDetails();
                cyclRslt.releaseResults();
            }

            for(int siteNum : context.getActiveSites()) {
                // check if pattern passed. If it failed, collect error map.
                if (funcRslt.get(siteNum) == true) {
                    if (Debug.showUser()) {
                        println(String.format("Pattern passed for site %d", siteNum));
                    }
                }
                else {
                    if (Debug.showUser()) {
                        println(String.format("Pattern failed for site %d", siteNum));
                    }
                }
                if (Debug.showUser()) {


                    for(ISignal pin : measurement.resolveSignalExpression(scanSignals)) {

                        if (failCycles != null) {


                            //final int dispLim = 5000;
                            FailedCycle[] pinSiteFC = failCycles.get(pin.getDutSignalName()).get(siteNum);
                            long numFailCyc = pinSiteFC.length;
                            // println(String.format("Site: %d for pin \t%s\tmax = %d\tNum Fail = %d", siteNum, pin, maxFailCycles, numFailCyc));
                            //                            String[] strary = Arrays.stream(pinSiteFC)
                            //                                    .limit(dispLim)
                            //                                    .mapToLong(getMapFailedCycleToCycleNumFunc())
                            //                                    .mapToObj(getLongToStringFunc())
                            //                                    .toArray(DataStrmUtil.getStringAryNewFunc());
                            //                            String cyclesStr = String.join(", ", strary) + (numFailCyc > dispLim ? ", ..." : "");
                            //                            println(String.format("Site: %d pin: %s  Failing cycles: {%s}", siteNum, pin, cyclesStr));
                            // Created to match SMT7 printout


                            if (VecDumpFailOnly)
                            {
                                // Below 2 lines added to match SMT7 output
                                println("Site: "+ siteNum);
                                println("err nbr | user cycle nbr |        pin name | E | R");


                            for (int failcount=0; failcount<numFailCyc; failcount++){
                                String exp= GetExpected(pinSiteFC[failcount].getBinaryRepresentation());
                                String rcv = GetReceived(pinSiteFC[failcount].getBinaryRepresentation());
                                println(String.format("%4d %17d %17s %5s %3s", failcount, pinSiteFC[failcount].getCycleNumber(), pin.getDutSignalName(), exp, rcv));
                            }
                            }

                        if (setTotalErrorCount)
                        {
                            IDigInOutSummaryResults myDigInOutSummaryResults = measurement.digInOut().preserveSummaryResults();
                            // Release the tester hardware to allow the hardware to execute other test suites.
                           // releaseTester();
                            // Retrieve the values in the background while other test suites are executed.
                            MultiSiteLong myDigInOutErrorCountOneSignal = myDigInOutSummaryResults.getErrorCount(pin);

                            long tmpErrorCount = myDigInOutErrorCountOneSignal.get(siteNum);
                         //   totalErrorCount = totalErrorCount + tmpErrorCount;

                            MStotalErrorCount.add(tmpErrorCount);

                            println("[DBG]: PIN : " + pin + " = " + myDigInOutErrorCountOneSignal );
                            println("[DBG]: Total Error Count : " + totalErrorCount );




                        } // close if FailedCycles





                    }
                }


                    if (!SyncMultiSiteDouble.isKeyExists(totalErrCountVariable))
                    {
                       // throw new SocException(this, "MultiSiteDouble Variable " + totalErrCountVariable + " is not defined in database");
                    }
                }
                else
                {
                    SyncMultiSiteLong Tf_var = SyncMultiSiteLong.create(totalErrCountVariable, this);
                    Tf_var.set(MStotalErrorCount);
                }









                if(Debug.showGraphOrSaveData()) {

                    try {
                        int X = context.testProgram().variables().getLong("STDF.X_COORD").getAsInt(siteNum);
                        int Y = context.testProgram().variables().getLong("STDF.X_COORD").getAsInt(siteNum);
                        String[] tmpts = context.getTestSuiteName().split("\\.");
                        String testsuiteName = tmpts.length>0 ? tmpts[tmpts.length-1] : context.getTestSuiteName();

                        String TimeStamp = new SimpleDateFormat("YYYY-MM-dd-HH-mm-ss").format(new Date());

                        createMissingFolders("scan");

                        try(PrintWriter writeError = new PrintWriter(IWorkspace.getActiveProjectPath()+"/reports/scan/"+testsuiteName+"_S"+String.valueOf(siteNum)+"_errorlog_"+String.valueOf(X)+"_"+ String.valueOf(Y)+"_"+TimeStamp+".txt"))
                        {
                            writeError.println("Site: "+ siteNum);
                            writeError.println("err nbr | user cycle nbr |        pin name | E | R");

                            for(ISignal pin : measurement.resolveSignalExpression(scanSignals)) {
                                if (failCycles != null) {
                                    FailedCycle[] pinSiteFC = failCycles.get(pin.getDutSignalName()).get(siteNum);
                                    long numFailCyc = pinSiteFC.length;
                                    // println(String.format("Site: %d for pin \t%s\tmax = %d\tNum Fail = %d", siteNum, pin, maxFailCycles, numFailCyc));
                                    //                            String[] strary = Arrays.stream(pinSiteFC)
                                    //                                    .limit(dispLim)
                                    //                                    .mapToLong(getMapFailedCycleToCycleNumFunc())
                                    //                                    .mapToObj(getLongToStringFunc())
                                    //                                    .toArray(DataStrmUtil.getStringAryNewFunc());
                                    //                            String cyclesStr = String.join(", ", strary) + (numFailCyc > dispLim ? ", ..." : "");
                                    //                            println(String.format("Site: %d pin: %s  Failing cycles: {%s}", siteNum, pin, cyclesStr));
                                    // Created to match SMT7 printout

                                    for (int failcount=0; failcount<numFailCyc; failcount++){
                                        String exp= GetExpected(pinSiteFC[failcount].getBinaryRepresentation());
                                        String rcv = GetReceived(pinSiteFC[failcount].getBinaryRepresentation());
                                        writeError.println(String.format("%4d %17d %17s %5s %3s", failcount, pinSiteFC[failcount].getCycleNumber(), pin.getDutSignalName(), exp, rcv));
                                    }

                                }
                            }
                        }
                    }
                    catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (ScanErrorLog )
        {
            if (scanSignals.trim().isEmpty()) {
                scanSignals = "SCAN_OUT";
            }

            ScanTestSTR.setLogLevel(LOGLEVEL_EXPECTED); // setlog level to highest
            if(Debug.showUser()) {
                System.out.println("Scan datalogging enabled. Loglevel="+ScanTestSTR.getLogLevel());
            }

            // run measurement
            measurement.execute();
            measurementResult = measurement.preserveResult();

            allPassed = true;

            MultiSiteBoolean passed = measurementResult.hasPassed();
            for (int site : context.getActiveSites())
            {
                if (passed.get(site) == false )
                {
                    allPassed = false;
                    break;
                }
            }

            if(allPassed==false) {
                if(burstMode) {
                    callResult = measurement.digInOut(scanSignals).preserveCallPassFailResults().getPatternPassFail();
                    ScanTestFTR.setLogPerCall(true);
                    digInOutResultsFTR = measurement.digInOut(scanSignals).preserveResults(ScanTestFTR);
                }

                digInOutResults = measurement.digInOut(scanSignals).preserveResults(ScanTestSTR);
                releaseHardware();    // FTR/STR sequence issue seen in SMT 8070, fixed in newer SMT, can enable multithreading now
            }
            if(Debug.showResult())
            {
                // print pin pass/fail info
                println ("  "+context.getTestSuiteName()+" P/F result: "+measurementResult.hasPassed());
            }

            for (int site : context.getActiveSites()){
                if(burstMode ==true && allPassed == false )
                {
                    //log per parallel group
                    for (int i = 0;i< allParallelGroup.size(); i ++ )
                    {
                        //  int testnum = testNumberStart; // used for Concurrency Scan

                        IDigInOutResults parallelGroupResult = digInOutResults.parallel(allParallelGroup.get(i)).pattern("");
                        List<IPatternCall> patCallsList = parallelGroupResult.pattern("").getPatternCalls();

                        IDigInOutCallPassFailResults callPFResults = parallelGroupResult.callPassFail();
                        resultPerPatArray = callPFResults.pattern(patCallsList).getPatternPassFail();

                        //get per PG fail cycle
                        Set<Long> failedCyclesSet = new TreeSet<>();
                        Map<String, MultiSiteLongArray> absFailingCyclesLongArrayMap = digInOutResults.parallel(allParallelGroup.get(i)).pattern("").cyclePassFail().getFailedCycles();

                        ScanUtility.calUniqueFailingCycle(failedCyclesSet, absFailingCyclesLongArrayMap, site);// convert to per site

                        String patname = "";
                        //now only support one patternCall per parallel group
                        for(IResultPatternPassFail pat_pf : resultPerPatArray) {
                            patname = ScanUtility.getLastElement(pat_pf.getPatternCall().getPatternName());

                            //log per call FTR
                            if(Debug.showUser()){
                                println(patname + ":"+testText_FTR);
                            }
                            logFTR_burst(ScanTestFTR,patname + ":"+testText_FTR,digInOutResultsFTR,site,baseFTRTestNumber + i,allParallelGroup.get(i));

                            // skip if not executed, prepare for pattern bypass
                            //MultiSiteBoolean execed = pat_pf.hasExecuted();
                            //if (!MultiSiteValUtil.has(execed, true)) {
                            //    continue;
                            //}

                            //get per patternCall pass fail
                            MultiSiteBoolean perPatternPassFail = pat_pf.hasPassed();
                            if(Debug.showUser()){
                                System.out.println(" Pattern name : " +  pat_pf.getPatternCall().getName());
                                System.out.println(" Pattern is Pass/Fail : " +  perPatternPassFail);
                            }

                            // check tfv maxScanCycleFailures TODO
                            //MultiSiteLong maxScanCycleFailuresTFV = SynchronizedVariables.SyncMultiSiteLong.create("maxScanCycleFailures").get();

                            // log STR if failed
                            if (perPatternPassFail.get(site).equals(false) && scanDataloggingEnabled)
                            {
                                // if no fail logged, skip this slice
                                IPatternCall patCall = pat_pf.getPatternCall();
                                Map<String, MultiSiteLongArray> relFailingCyclesLongArrayMap = digInOutResults.cyclePassFail().pattern(patCall).getFailedCycles();
                                long currentPatternTotalFailCycleNum = 0;
                                for (Entry<String, MultiSiteLongArray> failedCycleLongArrayEntry : relFailingCyclesLongArrayMap.entrySet()) {
                                    MultiSiteLongArray cyclesMultiLong = failedCycleLongArrayEntry.getValue();
                                    currentPatternTotalFailCycleNum += cyclesMultiLong.get(site).length;
                                }

                                // skip if no fail cycle logged
                                if (currentPatternTotalFailCycleNum != 0)
                                {
                                    logSTR_burst(ScanTestSTR,patname + ":"+testText_STR,digInOutResults,site,baseSTRTestNumber + i,allParallelGroup.get(i));
                                    String TestSuiteName = ScanUtility.getLastElement(context.getTestSuiteName());
                                    ScanUtility.reportToFileBurst(parallelGroupResult,site, patname,"File+ReportUI",true,includeExpectedData,TestSuiteName);
                                }
                            }
                        }
                    }//end of each parallel group
                }// end of burst
                // normal mode
                else {

                    //System.out.println ("testText_FTR is \t"+ testText_FTR);
                    logFTR(ScanTestFTR,testText_FTR,measurementResult,site);

                    // //for debug purpose only
                    //                    MultiSiteBoolean debug_false = new MultiSiteBoolean(false);
                    //                    logFTR(ScanTestFTR,testText_FTR,debug_false,site);

                    if(scanDataloggingEnabled)
                    {
                        MultiSiteBoolean passed_2 = measurementResult.hasPassed();
                        if (passed_2.get(site) == false)
                        {
                            logSTR(ScanTestSTR,testText_STR,digInOutResults,site);

                            String patternOpSeqName = ScanUtility.getLastElement (measurement.getPatternName() != null
                                    ? measurement.getPatternName() : measurement.getOperatingSequenceName());
                            String TestSuiteName = ScanUtility.getLastElement(context.getTestSuiteName());

                            createMissingFolders("scan");

                            //ScanUtility.reportToFile(digInOutResults,site,patternOpSeqName,output,expectedDataLogging,includeExpectedData,TestSuiteName);
                            ScanUtility.reportToFile(digInOutResults,site,patternOpSeqName,"File+ReportUI",true,includeExpectedData,TestSuiteName);
                        }
                    } // end of scan concurrency block
                } // end of normal mode
            }
        }

        // digital capture

        if ((VecDumpFailOnly))
        {
            DigCap = false; // set DigCap to false first
            measurement_Functional.execute();
            for(int site : context.getActiveSites())
            {
                if(!measurement_Functional.hasPassed(site))
                {
                    DigCap = true; // set DigCap to true if there is any failing site/s.
                }
            }
        }

        // digital capture

        if (DigCap)
        {
            if (digCapPinList.isEmpty()) {
                throw new SocException(this, this.getClass() + ":" + context.getTestSuiteName() + ": User specified an empty digcap pinList!");
            }
            if (numCaptures<1) {
                throw new SocException(this, this.getClass() + ":" + context.getTestSuiteName() + ": User specified an invalid # of captures (must be >=1): " + numCaptures);
            }

            measurement.execute();

            boolean DisableShmooCondition = VddSpecVar_shmoo.isEmpty()&&FreqSpecVar_shmoo.isEmpty();

            MultiSiteDouble MSspecValue_vdd = new MultiSiteDouble();
            MultiSiteDouble MSspecValue_Freq = new MultiSiteDouble();

            if(!DisableShmooCondition)
            {
                MSspecValue_vdd = measurement.spec().getDouble(VddSpecVar_shmoo);
                MSspecValue_Freq = measurement.spec().getDouble(FreqSpecVar_shmoo);
                if(Debug.showUser2())
                {
                    System.out.println("VECDUMP Vdd Spec Value = "+MSspecValue_vdd);
                    System.out.println("VECDUMP Freq Spec Value = "+MSspecValue_Freq);
                }
            }

            IDigInOutCaptureResults digCapResults = measurement.digInOut(digCapPinList).preserveCaptureResults();  // kozma

            //  releaseHardware();  // break out into release hardware

            Map<String,MultiSiteBitSequence> bitsOfAllSignals=digCapResults.getSerialBitsAsBitSequence();
            digCapResults.releaseResults();

            if (isOffline)
            {
                if(Debug.showUser2())
                {
                    System.out.println("\n[INFO] Vsense => Offline mode detected - forcing values");
                }

                //bitsOfAllSignals = GenerateRandomData(bitsOfAllSignals.keySet());
                bitsOfAllSignals = GenerateRandomData(bitsOfAllSignals.keySet(),numCaptures);
                boolean AllreadyPrint = false;

                for(int site: context.getActiveSites())
                {
                    // print this info only once, we are forcing the same values for all sites
                    if (!AllreadyPrint)
                    {
                        String[] AllPins = digCapPinList.split("\\+");

                        for (String Pin :AllPins )
                        {
                            if(Debug.showUser2())
                            {
                                System.out.println("[DBG] Pin = " + Pin + "\t => " + bitsOfAllSignals.get(Pin).get(site) );
                            }
                            AllreadyPrint = true;
                        }
                    }
                }
            }

            MultiSiteLongArray dataConverted = null;
            dataConverted = ConvertBitsToLong(digCapPinList, bitsOfAllSignals);

            if(Debug.showUser2()){println("\tdigCapPinList = " + digCapPinList);
                println(dataConverted);
            }

            String[] digCapSignals=StringUtilities.strtokTrim(digCapPinList,"\\+");
            int numOfSignals=digCapSignals.length;
            if(Debug.showUser2()){println("\tnumOfSignals = " + numOfSignals);}
            if (numOfSignals < 1) {
                throw new SocException(this, this.getClass() + ":" + context.getTestSuiteName() + ": User specified an invalid # of signals (must be >=1): " + numOfSignals);
            }

            int[] actSites=context.getActiveSites();
            rowsOfCapture = bitsOfAllSignals.get(digCapSignals[0]).get(actSites[0]).length();

            if (numCaptures > rowsOfCapture) {
                numCaptures = rowsOfCapture;
            }

            if(Debug.showUser2()){println("\trowsOfCapture = " + rowsOfCapture);}
            if(Debug.showUser2()){println("\tcaptures requested by user = " + numCaptures);}

            if (numCaptures > rowsOfCapture) {
                throw new SocException(this, this.getClass() + ":" + context.getTestSuiteName() + ": User requested " + numCaptures + " captures, yet only " + rowsOfCapture + " were actually cpatured!");
            }

            rowsToProcess = rowsOfCapture;
            if (numCaptures < rowsOfCapture) {
                println("WARNING: the user requested that " + numCaptures + " captures be made, but the system captured " + rowsOfCapture + " rows, this code will only process the first "+numCaptures+ " rows" );
                rowsToProcess = numCaptures;
            }

            //long dummyBit = 1;
            for (int iSite: actSites) {

//                System.out.println("===> Spec Value +"+MSspecValue.get(iSite));
                Double SiteSpecValue = MSspecValue_vdd.get(iSite);
                Double SiteSpecValue_freq = MSspecValue_Freq.get(iSite);

                if ((VecDumpFailOnly))
                {
                    if(!measurement_Functional.hasPassed(iSite))
                    {
                        DigCap = true; // reuse the same variable "DigCap". set it to true to dump vector only for failing site/s
                    }
                }

                //  if(Debug.showGraphOrSaveData()) {
                if(DigCap) {
                    try {
                        long last_value = dataConverted.getElement(iSite, 0)-1;
                        int X = context.testProgram().variables().getLong("STDF.X_COORD").getAsInt(iSite);
                        int Y = context.testProgram().variables().getLong("STDF.X_COORD").getAsInt(iSite);
                        String[] tmpts = context.getTestSuiteName().split("\\.");
                        String testsuiteName = tmpts.length>0 ? tmpts[tmpts.length-1] : context.getTestSuiteName();

                        String TimeStamp = new SimpleDateFormat("YYYY-MM-dd-HH-mm-ss-sss").format(new Date());
                        String FileName = "";
                        if(!DisableShmooCondition) { // shmoo condition recorded in the file name
                            FileName = testsuiteName+"_S"+String.valueOf(iSite)+"_errorlog_"+String.valueOf(X)+"_"+ String.valueOf(Y)+"_Vdd="+String.valueOf(SiteSpecValue)+"_"+"Freq="+String.valueOf(SiteSpecValue_freq)+"_"+TimeStamp+".txt";
                        }
                        else {
                            FileName = testsuiteName+"_S"+String.valueOf(iSite)+"_errorlog_"+String.valueOf(X)+"_"+ String.valueOf(Y)+"_"+TimeStamp+".txt";
                        }

                        if(Debug.showUser2()){println("\t FileName of VECDUMP is " + FileName);}

                        createMissingFolders("vecdump");

                        try(PrintWriter writeVector = new PrintWriter(IWorkspace.getActiveProjectPath()+"/reports/vecdump/"+FileName))
                        {
                            for(int index =0 ; index < numCaptures ; index++){
                                if (dataConverted.getElement(iSite, index) != last_value){
                                    writeVector.println("Cycle " + Integer.toString(index) + " dec " + (dataConverted.getElement(iSite, index) + " hex " + Long.toHexString(dataConverted.getElement(iSite, index))));
                                    last_value = dataConverted.getElement(iSite, index);
                                }
                            }
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // run measurement
        if(!VecDumpFailOnly &&!VminFlag&&!FmaxFlag&&!ErrorLog&&!DigCap && !ScanErrorLog)
        {
            measurement.execute();
        }
    }

    private void createMissingFolders(final String _subfolder) {
        if(!FileUtilities.fileExists(IWorkspace.getActiveProjectPath()+"/reports"))
        {
            System.out.println("\n[INFO] creating folder: " + IWorkspace.getActiveProjectPath()+"/reports");
            FileUtilities.makeDirectory(IWorkspace.getActiveProjectPath()+"/reports");
        }
        if(!FileUtilities.fileExists(IWorkspace.getActiveProjectPath()+"/reports/" +_subfolder))
        {
            System.out.println("\n[INFO] creating folder: " + IWorkspace.getActiveProjectPath()+"/reports/"+_subfolder);
            FileUtilities.makeDirectory(IWorkspace.getActiveProjectPath()+"/reports/" + _subfolder);
        }
    }

    /** Retrieve data */
    @Override
    public void process()
    {
        if(VecDumpFailOnly)
        {
            pf_VecDumpFail = measurement_Functional.hasPassed();
        }

        if(!VecDumpFailOnly &&!VminFlag&&!FmaxFlag&&!ErrorLog&&!DigCap)
        {
            // use process() only if no retests have been done
            if(TestResults && !retestDone){
                if(Functional.getLogLevel()>LOGLEVEL_PF_ONLY) {
                    funcResults = measurement.preservePerSignalPassFail();
                    releaseHardware();

                    if(Debug.showResult())
                    {
                        // print pin pass/fail info
                        println (funcResults.hasPassed());
                    }
                }
                else {
                    //releaseHardware();
                    pf = measurement.hasPassed();
                }
            }
        }

        if(VminFlag )
        {
            VminResult = vmintask.getPassValue();

        }
        if (FmaxFlag)
        {
            FmaxResult = fmaxtask.getPassValue();
        }
    }

    /** Datalog results */
    @Override
    public void datalog()
    {
        // send results for all sites to datalog
        // in case retest was done this is handled in runMeasurement
        if(VminFlag)
        {
            logPTR(Parametric_Vmin, VminResult);
            if(Debug.showUser2()){
                println(" Vmin result = " + VminResult);
            }

            if (VminMarginFlag)
            {
                MultiSiteDouble Vmin_compare_value = new MultiSiteDouble(0.0) ;
                if (!Var_VminTarget.equals(""))
                {
                    if (!SyncMultiSiteDouble.isKeyExists(Var_VminTarget))
                    {
                        throw new SocException(this, "MultiSiteDouble Variable " + Var_VminTarget + " is not defined in database");
                    }
                    Vmin_compare_value = SyncMultiSiteDouble.create(Var_VminTarget, this).get();//MSMGlobalVariables.GET_USER_MultiSiteDouble(_varInfo.VariableName);
                }
                else
                {
                    Vmin_compare_value.set(DBL_VminTarget);
                }

                if (Debug.showUser2())
                {
                    for (int site : context.getActiveSites())
                    {
                        System.out.println(Var_VminTarget + " Site " + site + " -> " + Vmin_compare_value.get(site));
                    }
                }
                //SMT7: logPTR(mVspec_str.mName, mVspec_str.mName + "_CPR_MARGIN", cPR_value -_vSearch_result[c_site] );
                logPTR(Parametric_Vmin_Margin, Vmin_compare_value.subtract(VminResult));
                logPTR(Parametric_Vmin_Target, Vmin_compare_value);
            }

        }

        if (FmaxFlag){
            logPTR(Parametric_Fmax, FmaxResult);
            println(" Fmax result = " + FmaxResult);
        }

        if(VecDumpFailOnly)
        {
            logFTR(Functional_VecDumpFail, pf_VecDumpFail);
        }

        if(!VecDumpFailOnly &&!VminFlag&&!FmaxFlag&&!ErrorLog&&!DigCap && !ScanErrorLog)
        {
            if(TestResults && !retestDone && Functional.getLogLevel()>0){
                if(Functional.getLogLevel()<=LOGLEVEL_PF_ONLY) {
                    // P/F datalogging only
                    logFTR(Functional, pf);
                }
                else {
                    // datalogging with more details
                    logFTR(Functional, funcResults);
                }
            }
        }
    }

    protected static String GetExpected(int binaryRep){
        String expectString ="";
        while(binaryRep >= 1){
            if((binaryRep%2)==0){
                expectString = "L" + expectString ;
            }
            else{
                expectString = "H" +expectString ;
            }
            binaryRep = binaryRep /2;
        }

        return expectString;
    }

    protected static String GetReceived(int binaryRep){
        String rcvString ="";
        while(binaryRep >=1){
            if((binaryRep%2)==0){
                rcvString =  "H" + rcvString ;
            }
            else{
                rcvString ="L"+ rcvString;
            }
            binaryRep = binaryRep /2;
        }

        return rcvString;
    }

    /**
     * lambda alternative for providing function block for collecting sum of number of failed vectors
     * @return the function object
     */
    protected static ToLongFunction<FailedCycle> getMapFailedCycleToCycleNumFunc()
    {
        return new ToLongFunction<FailedCycle>() {
            @Override
            public long applyAsLong(FailedCycle fc) {
                return fc.getCycleNumber();
            }
        };
    }

    protected static LongFunction<String> getLongToStringFunc()
    {
        return new LongFunction<String>() {
            @Override
            public String apply(long value) {
                return String.valueOf(value);
            }
        };
    }

    /******************************************************************************************************************/
    /**
     * Subroutine to generate random data
     */
    protected Map<String, MultiSiteBitSequence> GenerateRandomData(final Set<String> _Pins, int nbits)
    {
        Map<String, MultiSiteBitSequence> result = new HashMap<>();

        Random rand = new Random();
        for (String Pin : _Pins)
        {
            BitSequence BitSeq = new BitSequence(nbits);
            for(int i=0; i < nbits ; i++) {
                int random_number = rand.nextInt(2);
                BitSeq.set(i,random_number==1 );
            }
            MultiSiteBitSequence MultiSiteBitSeq = new MultiSiteBitSequence();
            int ii = 3;
            for(int site: context.getActiveSites()){

                MultiSiteBitSeq.set(site, BitSeq.shiftAscending(ii));
                ii++;
            }
            result.put(Pin, MultiSiteBitSeq);
        }

        return result;
    }

    /******************************************************************************************************************/
    /**
     * Subroutine to convert bits to long data - input variable is a map<String, Bits>
     */
    protected MultiSiteLongArray ConvertBitsToLong(final String _pinList, final Map<String, MultiSiteBitSequence> _Bits)
    {
        MultiSiteLongArray Converted = new MultiSiteLongArray();

        String[] AllPins = _pinList.split("\\+");

        for(int site: context.getActiveSites())
        {
            if (Debug.showUser2())
            {
                System.out.println("[DBG2] Site: " + site);
            }

            boolean First_pin = true;
            long[] array = new long[1];
            int position_numPin = 0;

            for (String pin : AllPins)
            {
                BitSequence inputArray = _Bits.get(pin).get(site);

                if (Debug.showUser2())
                {
                    System.out.println("[DBG2] Pin: " + pin);
                    System.out.println(" \t Val: " + inputArray + "\n");
                }

                // assign correct size to the array and initialize it
                if (First_pin)
                {
                    array = new long[inputArray.length()];
                    First_pin = false;
                }

                // convert boolean to long and move data if digital capture was done on multiple pins - parallel
                for (int pos=0; pos < inputArray.length(); ++pos)
                {
                    if (position_numPin != 0)
                    {
                        array[pos] =  array[pos] << 1;
                    }

                    if (inputArray.get(pos))
                    {
                        array[pos] += 1;
                    }
                    else
                    {
                        array[pos] += 0;
                    }
                }
                position_numPin += 1;
            }

            Converted.set(site, array);
        }

        return Converted;
    }

    public static List<String> getParallelGroupName(IMeasurement _measurement)
    {
        List<String> tempList = new ArrayList<>();
        if(_measurement.operatingSequence()!=null) {

            // Scan Tests always have an OpSeq
            // loop over all parallel groups
            for(IParallelGroup group:_measurement.operatingSequence().getParallelGroups()) {
                String parallelGroupName = group.getName();
                tempList.add(parallelGroupName);
                //System.out.println("[DBG] getParallelGroups: " + group.getName());
            }
        }
        return tempList;
    }
}
