import { CommonModule } from '@angular/common'
import { FormsModule } from '@angular/forms'
import { RouterModule } from '@angular/router'
import { NgModule } from '@angular/core'
import { InfraComponentsModule } from 'infra-components'
import { SijilModule } from 'sijil'

import { CoreModule } from '../core/core.module'
import { UxModule } from '../shared/ux/ux.module'
import { routes } from './groups-routing.module'
import { GroupsResolve } from './groups.resolve'
import { GroupDetailsResolve } from './details/group-details.resolve'
import { GroupsStore } from './groups.store'
import { UserlistFiltersService } from '../core/services'

import { GroupsComponent } from './groups.component'
import { GroupCreate } from "./create/group-create.component";
import { GroupDetails } from './details/group-details.component'
import { GroupManageUsers } from './details/manage-users/group-manage-users.component'
import { GroupInputUsers } from './details/manage-users/input/group-input-users.component'
import { GroupInputFilters } from './details/manage-users/input/group-input-filters.component'
import { GroupOutputUsers } from './details/manage-users/output/group-output-users.component'
import { GroupUsersList} from './details/users-list/group-users-list.component'
import { GroupsTypeView} from './type-view/groups-type-view.component'

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        UxModule,
        SijilModule.forChild(),
        InfraComponentsModule.forChild(),
        RouterModule.forChild(routes)
    ],
    declarations: [
        GroupsComponent,
        GroupCreate,
        GroupDetails,
        GroupManageUsers,
        GroupInputUsers,
        GroupInputFilters,
        GroupOutputUsers,
        GroupUsersList,
        GroupsTypeView
    ],
    providers: [
        GroupsResolve,
        GroupDetailsResolve,
        GroupsStore,
        UserlistFiltersService
    ],
    exports: [
        RouterModule
    ]
})
export class GroupsModule {}