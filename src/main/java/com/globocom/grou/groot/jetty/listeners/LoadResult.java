/*
 * Copyright (c) 2017-2018 Globo.com
 * All rights reserved.
 *
 * This source is subject to the Apache License, Version 2.0.
 * Please see the LICENSE file for more information.
 *
 * Authors: See AUTHORS file
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.globocom.grou.groot.jetty.listeners;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LoadResult
{

    private ServerInfo serverInfo;

    private CollectorInformations collectorInformations;

    private List<LoadConfig> loadConfigs = new ArrayList<>();

    private String uuid;

    private String externalId;

    private String comment;

    /**
     * so we can search prefix-*
     */
    private String uuidPrefix;

    /**
     * timestamp using format
     */
    private String timestamp = ZonedDateTime.now().format( DateTimeFormatter.ofPattern( "yyyy-MM-dd'T'HH:mm.ssZ" ) );

    public LoadResult()
    {
        // no op
    }

    public LoadResult( ServerInfo serverInfo, CollectorInformations collectorInformations, LoadConfig loadConfig )
    {
        this.serverInfo = serverInfo;
        this.collectorInformations = collectorInformations;
        this.loadConfigs.add( loadConfig );
    }

    public ServerInfo getServerInfo()
    {
        return serverInfo == null ? serverInfo = new ServerInfo() : serverInfo;
    }

    public CollectorInformations getCollectorInformations()
    {
        return collectorInformations == null ? collectorInformations = new CollectorInformations() : collectorInformations;
    }

    public void setServerInfo( ServerInfo serverInfo )
    {
        this.serverInfo = serverInfo;
    }

    public void setCollectorInformations( CollectorInformations collectorInformations )
    {
        this.collectorInformations = collectorInformations;
    }

    public List<LoadConfig> getLoadConfigs()
    {
        return loadConfigs;
    }

    public void setLoadConfigs( List<LoadConfig> loadConfigs )
    {
        this.loadConfigs = loadConfigs;
    }

    public void addLoadConfig( LoadConfig loadConfig )
    {
        this.loadConfigs.add( loadConfig );
    }

    public String getUuid()
    {
        return uuid;
    }

    public void setUuid( String uuid )
    {
        this.uuid = uuid;
    }

    public LoadResult uuid( String uuid )
    {
        this.uuid = uuid;
        return this;
    }

    public String getComment()
    {
        return comment;
    }

    public void setComment( String comment )
    {
        this.comment = comment;
    }

    public LoadResult comment( String comment )
    {
        this.comment = comment;
        return this;
    }

    public String getTimestamp()
    {
        return timestamp;
    }

    public void setTimestamp( String timestamp )
    {
        this.timestamp = timestamp;
    }

    public LoadResult timestamp( String timestamp )
    {
        this.timestamp = timestamp;
        return this;
    }

    public String getUuidPrefix()
    {
        return uuidPrefix;
    }

    public void setUuidPrefix( String uuidPrefix )
    {
        this.uuidPrefix = uuidPrefix;
    }

    public LoadResult uuidPrefix( String uuidPrefix )
    {
        this.uuidPrefix = uuidPrefix;
        return this;
    }

    public String getExternalId()
    {
        return externalId;
    }

    public void setExternalId( String externalId )
    {
        this.externalId = externalId;
    }

    public LoadResult externalId( String externalId )
    {
        this.externalId = externalId;
        return this;
    }

    @Override
    public String toString()
    {
        return "LoadResult{" + "serverInfo=" + serverInfo + ", collectorInformations=" + collectorInformations
            + ", loadConfigs=" + loadConfigs + ", uuid='" + uuid + '\'' + ", externalId='" + externalId + '\''
            + ", comment='" + comment + '\'' + ", uuidPrefix='" + uuidPrefix + '\'' + ", timestamp='" + timestamp + '\''
            + '}';
    }
}
