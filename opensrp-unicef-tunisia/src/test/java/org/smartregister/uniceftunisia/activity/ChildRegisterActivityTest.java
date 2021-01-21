package org.smartregister.uniceftunisia.activity;


import android.app.Activity;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.smartregister.uniceftunisia.BaseActivityUnitTest;


public class ChildRegisterActivityTest extends BaseActivityUnitTest {

    private ChildRegisterActivity childRegisterActivity;

    private ActivityController<ChildRegisterActivity> controller;

    @Before
    public void setUp(){

        controller = Robolectric.buildActivity(ChildRegisterActivity.class).create().start();
        childRegisterActivity = controller.get();
    }

    @Override
    protected Activity getActivity() {
        return childRegisterActivity;
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected ActivityController getActivityController() {
        return controller;
    }

    @Test
    public void startJsonFormActivityTest() throws JSONException {
        JSONObject jsonForm  = new JSONObject("{}");

        ChildRegisterActivity spyActivity = Mockito.spy(childRegisterActivity);
        spyActivity.startFormActivity(jsonForm);
        assertActivityStarted(spyActivity, new ChildFormActivity());
    }

}
