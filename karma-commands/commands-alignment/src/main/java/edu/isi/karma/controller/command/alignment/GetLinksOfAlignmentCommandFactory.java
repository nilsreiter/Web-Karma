/*******************************************************************************
 * Copyright 2012 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code was developed by the Information Integration Group as part 
 * of the Karma project at the Information Sciences Institute of the 
 * University of Southern California.  For more information, publications, 
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/

package edu.isi.karma.controller.command.alignment;

import edu.isi.karma.controller.command.Command;
import edu.isi.karma.controller.command.CommandFactory;
import edu.isi.karma.controller.command.alignment.GetLinksOfAlignmentCommand.LINKS_RANGE;
import edu.isi.karma.rep.Workspace;

import javax.servlet.http.HttpServletRequest;

public class GetLinksOfAlignmentCommandFactory extends CommandFactory {
	private enum Arguments {
		alignmentId, linksRange, domain, range, linkDirection, nodeId
	}

	@Override
	public Command createCommand(HttpServletRequest request,
			Workspace workspace) {
		String alignmentId = request.getParameter(Arguments.alignmentId.name());
		LINKS_RANGE linksRange = LINKS_RANGE.valueOf(
				request.getParameter(Arguments.linksRange.name()));
		
		String domain = null;
		String range = null;
		if (linksRange == LINKS_RANGE.linksWithDomainAndRange) {
			domain = request.getParameter(Arguments.domain.name());
			range = request.getParameter(Arguments.range.name());
		}
			
		return new GetLinksOfAlignmentCommand(getNewId(workspace), alignmentId, linksRange,
				domain, range);
	}

	@Override
	public Class<? extends Command> getCorrespondingCommand()
	{
		return GetLinksOfAlignmentCommand.class;
	}
}
