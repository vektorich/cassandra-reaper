//
//  Copyright 2015-2016 Stefan Podkowinski
//  Copyright 2016-2018 The Last Pickle Ltd
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

import jQuery from "jquery";
import React from "react";
import ReactDOM from "react-dom";
import ServerStatus from "jsx/server-status";
import Sidebar from "jsx/sidebar";
import RepairForm from "jsx/repair-form";
import RepairList from "jsx/repair-list";
import repairScreen from "jsx/repair-screen";
import {
  statusObservableTimer,
  repairs,
  addRepairSubject, addRepairResult,
  deleteRepairSubject, deleteRepairResult, updateRepairStatusSubject,
  clusterNames, deleteSubject, deleteResult, updateStatusSubject,
  addClusterSubject, addClusterResult, deleteClusterSubject,
  deleteClusterResult, updateRepairIntensitySubject,
  logoutSubject, logoutResult
} from "observable";

jQuery(document).ready(function($){

  $.urlParam = function(name){
    var results = new RegExp('[\?&]' + name + '=([^&#]*)').exec(window.location.href);
    if (results != null) {
      return results[1] || 0;
    }
    else {
      return null;
    }
  }

  let currentCluster: string = $.urlParam('currentCluster');
  if(!currentCluster) {
    currentCluster = 'all';
  }

  ReactDOM.render(
    React.createElement(repairScreen, {clusterNames, addRepairSubject, addRepairResult, currentCluster, repairs, logoutSubject: logoutSubject, logoutResult: logoutResult, deleteSubject: deleteRepairSubject,
    deleteResult: deleteRepairResult,
    updateStatusSubject: updateRepairStatusSubject,
    updateIntensitySubject: updateRepairIntensitySubject,
    statusObservableTimer}),
    document.getElementById('wrapper')
  );


});
