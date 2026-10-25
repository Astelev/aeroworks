package net.aste.aeroworks.aerodynamics;

import java.util.Arrays;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Coupled implicit-midpoint integration: skew lift conserves energy, physical normal/base drag dissipates it. */
public final class PassiveImpulse {
    private final double[] operator=new double[36],pointMap=new double[18],pointForce=new double[9],mapped=new double[18];
    private final double[] massMatrix=new double[36],system=new double[42],motion=new double[6],change=new double[6];
    private boolean active;
    public void begin() {Arrays.fill(operator,0);active=false;}
    public void addSurface(Vector3dc lever,Vector3dc velocity,Vector3dc normal,Vector3dc lift,
            double normalDamping,double baseDamping,double timeStep) {
        double speedSquared=velocity.lengthSquared(),normalLength=normal.length();
        if(!lever.isFinite()||!velocity.isFinite()||!normal.isFinite()||!lift.isFinite()||normalLength<1e-9
                ||!Double.isFinite(timeStep)||timeStep<=0||!Double.isFinite(speedSquared)
                ||!Double.isFinite(normalDamping)||!Double.isFinite(baseDamping)||normalDamping<0||baseDamping<0)return;
        if(lift.lengthSquared()==0 && normalDamping==0 && baseDamping==0)return;
        double nx=normal.x()/normalLength,ny=normal.y()/normalLength,nz=normal.z()/normalLength;
        double ax=0,ay=0,az=0;
        if(speedSquared>1e-18) {
            // a x u = lift. The skew matrix does zero work at the midpoint as well as initially.
            ax=(velocity.y()*lift.z()-velocity.z()*lift.y())/speedSquared;
            ay=(velocity.z()*lift.x()-velocity.x()*lift.z())/speedSquared;
            az=(velocity.x()*lift.y()-velocity.y()*lift.x())/speedSquared;
        }
        pointForce[0]=-baseDamping-normalDamping*nx*nx;
        pointForce[1]=-az-normalDamping*nx*ny;
        pointForce[2]=ay-normalDamping*nx*nz;
        pointForce[3]=az-normalDamping*ny*nx;
        pointForce[4]=-baseDamping-normalDamping*ny*ny;
        pointForce[5]=-ax-normalDamping*ny*nz;
        pointForce[6]=-ay-normalDamping*nz*nx;
        pointForce[7]=ax-normalDamping*nz*ny;
        pointForce[8]=-baseDamping-normalDamping*nz*nz;
        // B maps COM linear/angular velocity to velocity at the surface: u = v + omega x r.
        Arrays.fill(pointMap,0);
        pointMap[0]=pointMap[7]=pointMap[14]=1;
        pointMap[4]=lever.z();pointMap[5]=-lever.y();
        pointMap[9]=-lever.z();pointMap[11]=lever.x();
        pointMap[15]=lever.y();pointMap[16]=-lever.x();
        for(int row=0;row<3;row++)for(int col=0;col<6;col++) {
            double value=0;
            for(int k=0;k<3;k++)value+=pointForce[row*3+k]*pointMap[k*6+col];
            mapped[row*6+col]=value;
        }
        for(int row=0;row<6;row++)for(int col=0;col<6;col++) {
            double value=0;
            for(int k=0;k<3;k++)value+=pointMap[k*6+row]*mapped[k*6+col];
            operator[row*6+col]+=timeStep*value;
        }
        active=true;
    }
    public void integrate(double mass,Matrix3dc inertia,Vector3dc airVelocity,Vector3dc spin,
            Vector3d impulse,Vector3d torque) {
        impulse.zero();torque.zero();
        if(!active||!Double.isFinite(mass)||mass<=0||!airVelocity.isFinite()||!spin.isFinite())return;
        Arrays.fill(massMatrix,0);
        massMatrix[0]=massMatrix[7]=massMatrix[14]=mass;
        massMatrix[21]=inertia.m00();massMatrix[22]=inertia.m10();massMatrix[23]=inertia.m20();
        massMatrix[27]=inertia.m01();massMatrix[28]=inertia.m11();massMatrix[29]=inertia.m21();
        massMatrix[33]=inertia.m02();massMatrix[34]=inertia.m12();massMatrix[35]=inertia.m22();
        motion[0]=airVelocity.x();motion[1]=airVelocity.y();motion[2]=airVelocity.z();
        motion[3]=spin.x();motion[4]=spin.y();motion[5]=spin.z();
        // (M - H/2) delta = H z. No whole-body velocity rescaling and no artificial drag coefficient.
        for(int row=0;row<6;row++) {
            double rhs=0;
            for(int col=0;col<6;col++) {
                system[row*7+col]=massMatrix[row*6+col]-.5*operator[row*6+col];
                rhs+=operator[row*6+col]*motion[col];
            }
            system[row*7+6]=rhs;
        }
        if(!solve())return;
        impulse.set(mass*change[0],mass*change[1],mass*change[2]);
        torque.set(change[3],change[4],change[5]);inertia.transform(torque);
        if(!impulse.isFinite()||!torque.isFinite()) {impulse.zero();torque.zero();}
    }
    private boolean solve() {
        for(int col=0;col<6;col++) {
            int pivot=col;
            for(int row=col+1;row<6;row++)if(Math.abs(system[row*7+col])>Math.abs(system[pivot*7+col]))pivot=row;
            double diagonal=system[pivot*7+col];
            if(!Double.isFinite(diagonal)||Math.abs(diagonal)<1e-15)return false;
            if(pivot!=col)for(int k=col;k<7;k++) {
                double value=system[col*7+k];system[col*7+k]=system[pivot*7+k];system[pivot*7+k]=value;
            }
            for(int row=col+1;row<6;row++) {
                double factor=system[row*7+col]/system[col*7+col];
                for(int k=col;k<7;k++)system[row*7+k]-=factor*system[col*7+k];
            }
        }
        for(int row=5;row>=0;row--) {
            double value=system[row*7+6];
            for(int col=row+1;col<6;col++)value-=system[row*7+col]*change[col];
            change[row]=value/system[row*7+row];
            if(!Double.isFinite(change[row]))return false;
        }
        return true;
    }
}
