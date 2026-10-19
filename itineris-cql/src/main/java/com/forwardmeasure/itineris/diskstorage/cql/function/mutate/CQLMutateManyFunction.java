// Copyright 2021 Itineris Authors
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

package com.forwardmeasure.itineris.diskstorage.cql.function.mutate;

import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.StaticBuffer;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KCVMutation;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreTransaction;

import java.util.Map;

public interface CQLMutateManyFunction {

    void mutateMany(final Map<String, Map<StaticBuffer, KCVMutation>> mutations, final StoreTransaction txh) throws BackendException;

}
