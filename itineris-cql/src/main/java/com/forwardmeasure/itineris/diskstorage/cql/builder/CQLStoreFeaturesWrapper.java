// Copyright 2021 JanusGraph Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.forwardmeasure.itineris.diskstorage.cql.builder;

import com.forwardmeasure.itineris.diskstorage.common.DistributedStoreManager;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreFeatures;

public class CQLStoreFeaturesWrapper {

    private final StoreFeatures storeFeatures;
    private final DistributedStoreManager.Deployment deployment;

    public CQLStoreFeaturesWrapper(StoreFeatures storeFeatures, DistributedStoreManager.Deployment deployment) {
        this.storeFeatures = storeFeatures;
        this.deployment = deployment;
    }

    public StoreFeatures getStoreFeatures() {
        return storeFeatures;
    }

    public DistributedStoreManager.Deployment getDeployment() {
        return deployment;
    }
}
