package com.test.inventorysystem.activities;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.j256.ormlite.android.apptools.OrmLiteBaseActivity;
import com.test.inventorysystem.R;
import com.test.inventorysystem.db.DBHelper;
import com.test.inventorysystem.db.DBManager;
import com.test.inventorysystem.interfaces.CallbackInterface;
import com.test.inventorysystem.models.OrganModel;
import com.test.inventorysystem.models.TypeModel;
import com.test.inventorysystem.models.UserModel;
import com.test.inventorysystem.services.SOAPActions;
import com.test.inventorysystem.utils.AppContext;
import com.test.inventorysystem.utils.TransUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Login extends OrmLiteBaseActivity<DBHelper> {

    private EditText loginUsr = null;
    private EditText loginPwd = null;
    private TextView loginTips = null;
    private Button loginBtn = null;
    private Button loginOffBtn = null;
    private ImageButton deleteUsrBtn = null;
    private ImageButton deletePwdBtn = null;
    private ProgressBar loginProgressBar = null;
    private boolean offline_flag = false;

    private String response = "";

    private DBManager dbManager = new DBManager();
    private String userAccount = "";
    private String userName = "";
    private String userDepartmentId = "";
//    private String currentUser = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        Initialization();
    }

    private void Initialization() {

        AppContext.offlineLogin = false;
        this.loginUsr = (EditText) findViewById(R.id.editText_usr);
        this.loginPwd = (EditText) findViewById(R.id.editText_pwd);
        this.loginTips = (TextView) findViewById(R.id.textView_login_tips);
        this.loginBtn = (Button) findViewById(R.id.button_login);
        this.loginOffBtn = (Button) findViewById(R.id.button_login_offlilne);
        this.deleteUsrBtn = (ImageButton) findViewById(R.id.imageButton_del_usr);
        this.deletePwdBtn = (ImageButton) findViewById(R.id.imageButton_del_pwd);
        this.loginProgressBar = (ProgressBar) findViewById(R.id.progressBar_login);

        this.loginBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                loginProgressBar.setVisibility(View.VISIBLE);
                loginTips.setVisibility((View.VISIBLE));
                HashMap<String, String> hashMap = new HashMap<>();
                hashMap.put("methodName", "doLogin");
                hashMap.put("username", loginUsr.getText().toString());
                hashMap.put("password", loginPwd.getText().toString());
                hashMap.put("addr", "none");
                hashMap.put("simId", "460024065533470");
                doLogin(hashMap);
            }
        });

        this.loginOffBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loginProgressBar.setVisibility(View.VISIBLE);
                loginTips.setVisibility(View.VISIBLE);
                try {
                    UserModel loginUser = dbManager.findUser(getHelper().getUserDao(), loginUsr.getText().toString());
                    System.out.println(loginUser);
                    if (loginUser != null) {
                        OrganModel loginOrgan = dbManager.findOrgan(getHelper().getOrganDao(), loginUser.getDepartmentId());
                        AppContext.offlineLogin = true;
                        AppContext.currUser = loginUser;
                        AppContext.currOrgan = loginOrgan;
                        buildMainFunction();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });

        this.deleteUsrBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loginUsr.setText("");
            }
        });

        this.deletePwdBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loginPwd.setText("");
            }
        });
    }

    private void doLogin(HashMap hashMap) {
        final SOAPActions sa = new SOAPActions(hashMap);
        String xmlRequest = sa.getXmlRequest();

        sa.sendRequest(this, xmlRequest, new CallbackInterface() {
            @Override
            public void callBackFunction() {
                response = TransUtil.decode(sa.getResponse());
                JsonParser jsonParser = new JsonParser();
                JsonObject jsonObject = (JsonObject) jsonParser.parse(response);

                int success = jsonObject.get("success").getAsInt();
                String message = jsonObject.get("message").getAsString();
                JsonObject org = jsonObject.get("org").getAsJsonObject();
                JsonObject user = jsonObject.get("user").getAsJsonObject();

                userAccount = user.get("accounts").getAsString();
                userName = user.get("username").getAsString();
                String userIsValid = user.get("isValid").getAsString();
                userDepartmentId = user.get("departmentId").getAsString();

                UserModel userModel = new UserModel();
                userModel.setAccounts(user.get("accounts").getAsString());
                userModel.setUsername(user.get("username").getAsString());
                userModel.setDepartmentId(user.get("departmentId").getAsString());

                OrganModel organModel = new OrganModel();
                organModel.setOrganId(org.get("organID").getAsString());
                organModel.setOrganCode(org.get("organCode").getAsString());
                organModel.setOrganName(org.get("organName").getAsString());

                AppContext.currUser = userModel;
                AppContext.currOrgan = organModel;
                AppContext.address = "none";
                AppContext.simId = "460024065533470";

                try {
                    dbManager.saveUser(getHelper().getUserDao(), userAccount, userModel, userName, userIsValid, userDepartmentId);
//                    currentUser = dbManager.findUser(getHelper().getUserDao(), userAccount);
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                HashMap<String, String> hashMap = new HashMap<String, String>();
                hashMap.put("methodName", "getAssetInventoryBase");
                hashMap.put("organCode", org.get("organCode").getAsString());
                loadBaseData(hashMap);
            }
        });
    }

    private void loadBaseData(HashMap hashMap) {
        final SOAPActions sa = new SOAPActions(hashMap);
        String xmlRequest = sa.getXmlRequest();

        sa.sendRequest(this, xmlRequest, new CallbackInterface() {
            @Override
            public void callBackFunction() {
                response = TransUtil.decode(sa.getResponse());
                JsonParser jsonParser = new JsonParser();
                JsonObject jsonObject = (JsonObject) jsonParser.parse(response);

                int success = jsonObject.get("success").getAsInt();
                String message = jsonObject.get("message").getAsString();
                JsonArray organListString = jsonObject.get("organList").getAsJsonArray();
                JsonArray typeListString = jsonObject.get("typeList").getAsJsonArray();
                JsonArray matchTypeListString = jsonObject.get("matchTypeList").getAsJsonArray();
                JsonArray completeTypeListString = jsonObject.get("completeTypeList").getAsJsonArray();
//                List<OrganModel> currOrganlist = null;

                if (success == 1) {
                    try {
                        ArrayList<OrganModel> organList = new ArrayList<OrganModel>();
                        for (int i = 0; i < organListString.size(); i ++) {
                            OrganModel organModel = new OrganModel(organListString.get(i).getAsJsonObject());
                            organList.add(organModel);
                        }
                        dbManager.saveOrganList(getHelper().getOrganDao(), userAccount, organList);

                        ArrayList<TypeModel> typeList = new ArrayList<TypeModel>();
                        for (int i = 0; i < typeListString.size(); i ++) {
                            TypeModel typeModel = new TypeModel(typeListString.get(i).getAsJsonObject());
                            typeList.add(typeModel);
                        }
                        for (int i = 0; i < matchTypeListString.size(); i ++) {
                            TypeModel typeModel = new TypeModel(matchTypeListString.get(i).getAsJsonObject());
                            typeList.add(typeModel);
                        }
                        for (int i = 0; i < completeTypeListString.size(); i ++) {
                            TypeModel typeModel = new TypeModel(completeTypeListString.get(i).getAsJsonObject());
                            typeList.add(typeModel);
                        }
                        dbManager.saveTypeList(getHelper().getTypeDao(), userAccount, typeList);

                        buildMainFunction();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void buildMainFunction() {
        Intent toMainPage = new Intent(this, MainActivity.class);
        startActivity(toMainPage);
        this.finish();
    }
}
