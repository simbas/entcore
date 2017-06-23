import { InfraComponentsModule } from 'infra-components'
import { CommonModule } from '@angular/common'
import { FormsModule } from '@angular/forms'
import { RouterModule } from '@angular/router'
import { NgModule } from '@angular/core'
import { SijilModule } from 'sijil'
import { UxModule } from '../shared/ux/ux.module'
import { routes } from './imports-exports-routing.module'
import { ImportsExportsRoot } from './imports-exports-root.component'
import { ImportCSV } from './import/import-csv.component'
import { MappingsTable } from './import/mappings-table.component'
import { ExportComponent } from './export/export.component'
import { MassMailComponent } from './export/mass-mail.component'
import { UserlistFiltersService } from '../core/services'
import { FiltersPipe } from './export/mass-mail.pipe'

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
        ImportsExportsRoot,
        ImportCSV,
        MappingsTable,
        MappingsTable,
        ExportComponent,
        MassMailComponent,
        FiltersPipe
    ],
    providers: [
        UserlistFiltersService,
        FiltersPipe
    ],
    exports: [
        RouterModule
    ]
})
export class ImportsExportsModule {}