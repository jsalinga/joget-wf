package org.enhydra.shark;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.enhydra.shark.api.client.wfmc.wapi.WMSessionHandle;
import org.enhydra.shark.api.internal.instancepersistence.ProcessPersistenceObject;
import org.enhydra.shark.api.internal.toolagent.ToolAgentGeneralException;
import org.enhydra.shark.api.internal.working.WfActivityInternal;
import org.enhydra.shark.api.internal.working.WfProcessMgrInternal;
import org.enhydra.shark.api.internal.working.WfRequesterInternal;
import org.enhydra.shark.utilities.MiscUtilities;
import org.enhydra.shark.xpdl.elements.Activity;
import org.joget.workflow.model.dao.WorkflowHelper;
import org.joget.workflow.util.WorkflowUtil;

public class CustomWfProcessImpl extends WfProcessImpl {

    protected CustomWfProcessImpl(WMSessionHandle shandle, WfProcessMgrInternal manager, WfRequesterInternal requester, String key) throws Exception {
        super(shandle, manager, requester, key);
    }

    protected CustomWfProcessImpl(ProcessPersistenceObject po) {
        super(po);
    }

    @Override
    protected void joinTransition(WMSessionHandle shandle, WfActivityInternal fromActivity, Activity toActivityDef)
            throws Exception, ToolAgentGeneralException {
        synchronizeProcess(shandle);

        List toTrans = toActivityDef.getIncomingTransitions();

        AndJoinHelperStruct ajhs = new AndJoinHelperStruct(fromActivity.block_activity_id(shandle), toActivityDef);

        int followed = restoreActivityToFollowedTransitionsMap(shandle, ajhs);
        SharkEngineManager.getInstance().getCallbackUtilities().info(shandle, "Process" + toString() + " - " + (followed + 1) + " of " + toTrans.size() + " transitions followed to activity with definition " + toActivityDef.getId() + (fromActivity.block_activity_id(shandle) == null ? "" : new StringBuffer().append(" inside block instance ").append(fromActivity.block_activity_id(shandle)).toString()));

        if (toTrans.size() == followed + 1) {
            SharkEngineManager.getInstance().getCallbackUtilities().info(shandle, "Process" + toString() + " - All transition have been followed to activity with definition " + toActivityDef.getId() + (fromActivity.block_activity_id(shandle) == null ? "" : new StringBuffer().append(" inside block instance ").append(fromActivity.block_activity_id(shandle)).toString()));

            Set currentTrans = (Set) this.newActivityToFollowedTransitions.get(ajhs);
            if ((currentTrans != null) && (currentTrans.size() == followed)) {
                this.newActivityToFollowedTransitions.remove(ajhs);
            } else if (currentTrans != null) {
                currentTrans.clear();
            } else {
                this.newActivityToFollowedTransitions.put(ajhs, currentTrans);
            }

            this.activityToFollowedTransitions.put(ajhs, new Integer(0));

            persistActivityToFollowedTransitions(shandle);

            startActivity(shandle, toActivityDef, getActiveActivity(shandle, fromActivity.block_activity_id(shandle)));
            
            this.activityCache.clear(); //Customise: clear cache so that the remaining open activities is update
        } else {
            this.activityToFollowedTransitions.put(ajhs, new Integer(followed + 1));
            Set currentTrans = (Set) this.newActivityToFollowedTransitions.get(ajhs);
            if (currentTrans == null) {
                currentTrans = new HashSet();
                this.newActivityToFollowedTransitions.put(ajhs, currentTrans);
            }
            currentTrans.add(fromActivity.key(shandle));

            persistActivityToFollowedTransitions(shandle);
        }
    }
    
    @Override
    public WfActivityInternal[] checkDeadlines(WMSessionHandle shandle) throws Exception {
        WorkflowHelper workflowMapper = (WorkflowHelper) WorkflowUtil.getApplicationContext().getBean("workflowHelper");
        workflowMapper.updateAppDefinitionForDeadline(key, MiscUtilities.getProcessMgrPkgId(managerName), mgrVer);
        return super.checkDeadlines(shandle);
    }
}
