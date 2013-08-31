/*
 * Druid - a distributed column store.
 * Copyright (C) 2012, 2013  Metamarkets Group Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package io.druid.indexing.common.actions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableSet;
import com.metamx.common.ISE;
import com.metamx.emitter.service.ServiceMetricEvent;
import io.druid.client.DataSegment;
import io.druid.indexing.common.task.Task;

import java.io.IOException;
import java.util.Set;

public class SegmentInsertAction implements TaskAction<Set<DataSegment>>
{
  @JsonIgnore
  private final Set<DataSegment> segments;

  @JsonIgnore
  private final boolean allowOlderVersions;

  public SegmentInsertAction(Set<DataSegment> segments)
  {
    this(segments, false);
  }

  @JsonCreator
  public SegmentInsertAction(
      @JsonProperty("segments") Set<DataSegment> segments,
      @JsonProperty("allowOlderVersions") boolean allowOlderVersions
  )
  {
    this.segments = ImmutableSet.copyOf(segments);
    this.allowOlderVersions = allowOlderVersions;
  }

  @JsonProperty
  public Set<DataSegment> getSegments()
  {
    return segments;
  }

  @JsonProperty
  public boolean isAllowOlderVersions()
  {
    return allowOlderVersions;
  }

  public SegmentInsertAction withAllowOlderVersions(boolean _allowOlderVersions)
  {
    return new SegmentInsertAction(segments, _allowOlderVersions);
  }

  public TypeReference<Set<DataSegment>> getReturnTypeReference()
  {
    return new TypeReference<Set<DataSegment>>() {};
  }

  @Override
  public Set<DataSegment> perform(Task task, TaskActionToolbox toolbox) throws IOException
  {
    if(!toolbox.taskLockCoversSegments(task, segments, allowOlderVersions)) {
      throw new ISE("Segments not covered by locks for task[%s]: %s", task.getId(), segments);
    }

    final Set<DataSegment> retVal = toolbox.getIndexerDBCoordinator().announceHistoricalSegments(segments);

    // Emit metrics
    final ServiceMetricEvent.Builder metricBuilder = new ServiceMetricEvent.Builder()
        .setUser2(task.getDataSource())
        .setUser4(task.getType());

    for (DataSegment segment : segments) {
      metricBuilder.setUser5(segment.getInterval().toString());
      toolbox.getEmitter().emit(metricBuilder.build("indexer/segment/bytes", segment.getSize()));
    }

    return retVal;
  }

  @Override
  public boolean isAudited()
  {
    return true;
  }

  @Override
  public String toString()
  {
    return "SegmentInsertAction{" +
           "segments=" + segments +
           '}';
  }
}