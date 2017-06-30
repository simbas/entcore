import { Component, 
    OnDestroy, OnInit, ChangeDetectionStrategy, ChangeDetectorRef, OnChanges,
    Input, Output, ViewChild } from '@angular/core'
import { BundlesService } from 'sijil'
import { ActivatedRoute, Data, Router, NavigationEnd } from '@angular/router'
import { Subscription } from 'rxjs/Subscription'
import { routing } from '../../../../routing/routing.utils'
import { ImportCSVService } from './import-csv.service'
import { Wizard } from 'infra-components'

@Component({
    selector: 'import-csv',
    templateUrl : './import-csv.component.html'
})

export class ImportCSV implements OnInit, OnDestroy {

    constructor(
        private route: ActivatedRoute,
        private router:Router,
        private bundles: BundlesService,
        private cdRef: ChangeDetectorRef){}

    translate = (...args) => { return (<any> this.bundles.translate)(...args) }

    // Subscriberts
    private structureSubscriber: Subscription;
    private routerSubscriber:Subscription;

    @ViewChild(Wizard) wizardEl: Wizard;

    // Step 1 : Files Deposit
    importInfos = {
        type:'CSV',
        structureId:'',
        structureExternalId:'',
        structureName:'',
        UAI:'',
        predelete: false,
        transition:false
        // Teacher:null,
        // Student:null,
        // Relative:null,
        // Personnel:null,
        // Guest:null
    }

    ngOnInit(): void {
        this.structureSubscriber = routing.observe(this.route, "data").subscribe((data: Data) => {
            console.log(data['structure']);
            if(data['structure']) {
                this.importInfos.structureId = data['structure'].id;
                this.importInfos.structureExternalId = data['structure'].externalId;
                this.importInfos.structureName = data['structure'].name;
                this.importInfos.UAI = data['structure'].UAI;
            
                this.cdRef.markForCheck()
            }
        })

        this.routerSubscriber = this.router.events.subscribe(e => {
            if(e instanceof NavigationEnd)
                this.cdRef.markForCheck()
        })
    }

    ngOnDestroy(): void {
    }

    nextStep(activeStep: Number) {
        switch(activeStep) {
            case 0 : this.depositCSVFiles(); break;
            case 1 : break;
            case 2 : break;
            case 3 : break;
            case 4 : break;
            case 5 : break;
            case 6 : break;
            case 7 : break;
            default : break;
        }

        this.wizardEl.doNextStep();
    }

    previousStep (activeStep: Number) {
        this.wizardEl.doPreviousStep();
    }

    /*
    * Next Step operations
    */

    loadFile(event) {
        console.log(event);
        let files : FileList = event.target.files;  
        if (files.length == 1) {
            this.importInfos[event.target.name] = event.target.files[0];
        }
    }

    depositCSVFiles() {
        ImportCSVService.uploadCSV(this.importInfos);
    }
}
